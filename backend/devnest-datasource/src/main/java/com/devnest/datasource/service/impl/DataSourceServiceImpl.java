package com.devnest.datasource.service.impl;

import com.devnest.common.crypto.CryptoService;
import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.core.pool.HikariPoolFactory;
import com.devnest.core.spi.TunnelPortForwarder;
import com.devnest.datasource.dto.DataSourceDto;
import com.devnest.datasource.dto.DataSourceRequest;
import com.devnest.datasource.entity.DataSourceConfig;
import com.devnest.datasource.mapper.DataSourceMapper;
import com.devnest.datasource.repository.DataSourceConfigRepository;
import com.devnest.datasource.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Service
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {

    private final DataSourceConfigRepository repo;
    private final DataSourceMapper mapper;
    private final CryptoService crypto;
    private final HikariPoolFactory poolFactory;
    private final TunnelPortForwarder portForwarder;

    @Override
    public List<DataSourceDto> listAll() {
        return repo.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public DataSourceDto getById(Long id) {
        return mapper.toDto(findOrThrow(id));
    }

    @Override
    @Transactional
    public DataSourceDto create(DataSourceRequest req) {
        if (repo.existsByName(req.getName())) {
            throw new BizException(ErrorCode.DATASOURCE_NAME_DUPLICATED);
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新增数据源必须填写密码");
        }
        DataSourceConfig entity = new DataSourceConfig();
        applyRequest(entity, req);
        return mapper.toDto(repo.save(entity));
    }

    @Override
    @Transactional
    public DataSourceDto update(Long id, DataSourceRequest req) {
        DataSourceConfig entity = findOrThrow(id);
        if (!entity.getName().equals(req.getName()) && repo.existsByName(req.getName())) {
            throw new BizException(ErrorCode.DATASOURCE_NAME_DUPLICATED);
        }
        applyRequest(entity, req);
        return mapper.toDto(repo.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DataSourceConfig entity = findOrThrow(id);
        poolFactory.destroyPool(poolKey(id));
        repo.delete(entity);
    }

    @Override
    public boolean testConnection(Long id) {
        DataSourceConfig ds = findOrThrow(id);
        String password = ds.decryptPassword(crypto);
        return doProbe(ds, password);
    }

    @Override
    public boolean testConnectionDirect(DataSourceRequest req) {
        DataSourceConfig ds = new DataSourceConfig();
        applyRequest(ds, req);
        return doProbe(ds, req.getPassword());
    }

    // ------------------------------------------------------------------
    private void applyRequest(DataSourceConfig entity, DataSourceRequest req) {
        entity.setName(req.getName());
        entity.setDbType(req.getDbType());
        entity.setHost(req.getHost());
        entity.setPort(req.getPort());
        entity.setDatabaseName(req.getDatabaseName());
        entity.setUsername(req.getUsername());
        // 密码:有值才加密更新,空值表示不修改(编辑场景)
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            entity.setPasswordCipher(crypto.encrypt(req.getPassword()));
        }
        entity.setTunnelBastionId(req.getTunnelBastionId());
        entity.setRemark(req.getRemark());
    }

    private boolean doProbe(DataSourceConfig ds, String password) {
        String jdbcUrl = buildJdbcUrl(ds, resolveHost(ds));
        String driver = resolveDriver(ds.getDbType());
        return poolFactory.probe(jdbcUrl, driver, ds.getUsername(), password, 3000);
    }

    /** 构建连接池 key(含版本号,配置变更时 rebuild 用) */
    static String poolKey(Long datasourceId) {
        return "ds:" + datasourceId;
    }

    /** 构建 JDBC URL,通过隧道时 host=127.0.0.1 + 转发端口 */
    String buildJdbcUrl(DataSourceConfig ds, String effectiveHost) {
        String dbName = (ds.getDatabaseName() == null || ds.getDatabaseName().isBlank())
                ? "" : ds.getDatabaseName();
        if ("MYSQL".equalsIgnoreCase(ds.getDbType())) {
            return "jdbc:mysql://" + effectiveHost + ":" + ds.getPort() + "/" + dbName
                    + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false";
        }
        if ("DM".equalsIgnoreCase(ds.getDbType())) {
            // DM:显式带上 schema 参数,避免默认连到 SYS 用户模式(无业务表)
            String url = "jdbc:dm://" + effectiveHost + ":" + ds.getPort();
            if (!dbName.isEmpty()) {
                url += "/" + dbName + "?schema=" + dbName;
            }
            return url;
        }
        throw new BizException(ErrorCode.DATASOURCE_UNSUPPORTED_TYPE, ds.getDbType());
    }

    /** 解析实际连接 host:隧道模式先建立转发再连 127.0.0.1:localPort */
    String resolveHost(DataSourceConfig ds) {
        if (ds.getTunnelBastionId() == null) {
            return ds.getHost();
        }
        // 隧道模式:验证跳板运行中,分配临时端口转发到目标 host:port
        if (!portForwarder.isBastionRunning(ds.getTunnelBastionId())) {
            throw new BizException(ErrorCode.TUNNEL_NOT_RUNNING, "请先启动绑定的 SSH 隧道");
        }
        // probe 时临时分配,用完释放;连接池模式由 DatabaseQueryService 管理
        return "127.0.0.1";
    }

    /** resolveHost 的重载:返回 [host, port] 数组,用于连接池场景 */
    String[] resolveHostPort(DataSourceConfig ds) {
        if (ds.getTunnelBastionId() == null) {
            return new String[]{ds.getHost(), String.valueOf(ds.getPort())};
        }
        if (!portForwarder.isBastionRunning(ds.getTunnelBastionId())) {
            throw new BizException(ErrorCode.TUNNEL_NOT_RUNNING, "请先启动绑定的 SSH 隧道");
        }
        int localPort = portForwarder.allocateTunnel(
                ds.getTunnelBastionId(), ds.getHost(), ds.getPort());
        return new String[]{"127.0.0.1", String.valueOf(localPort)};
    }

    static String resolveDriver(String dbType) {
        if ("MYSQL".equalsIgnoreCase(dbType)) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if ("DM".equalsIgnoreCase(dbType)) {
            return "dm.jdbc.driver.DmDriver";
        }
        throw new BizException(ErrorCode.DATASOURCE_UNSUPPORTED_TYPE, dbType);
    }

    private DataSourceConfig findOrThrow(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.DATASOURCE_NOT_FOUND));
    }
}
