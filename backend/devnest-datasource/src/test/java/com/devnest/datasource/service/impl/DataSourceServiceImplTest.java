package com.devnest.datasource.service.impl;

import com.devnest.common.context.CurrentUserProvider;
import com.devnest.common.crypto.CryptoService;
import com.devnest.core.pool.HikariPoolFactory;
import com.devnest.core.spi.TunnelPortForwarder;
import com.devnest.datasource.dto.DataSourceRequest;
import com.devnest.datasource.entity.DataSourceConfig;
import com.devnest.datasource.mapper.DataSourceMapper;
import com.devnest.datasource.repository.DataSourceConfigRepository;
import com.devnest.datasource.service.DatabaseQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据源配置变更时的连接缓存失效测试.
 * <p>
 * 守护点(这曾经是真实缺陷):改了数据源配置,不重启后端查询仍然打到旧库 ——
 * 因为连接池被 DatabaseQueryServiceImpl 永久缓存,而 update 从未通知它失效.
 * 本测试锁定"什么情况下必须丢弃连接池",防止以后被改回去.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/10 11:40
 */
class DataSourceServiceImplTest {

    private static final Long DS_ID = 1L;

    private DataSourceConfigRepository repo;
    private DataSourceMapper mapper;
    private CryptoService crypto;
    private HikariPoolFactory poolFactory;
    private TunnelPortForwarder portForwarder;
    private CurrentUserProvider currentUserProvider;
    private DatabaseQueryService databaseQueryService;

    private DataSourceServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(DataSourceConfigRepository.class);
        mapper = mock(DataSourceMapper.class);
        crypto = mock(CryptoService.class);
        poolFactory = mock(HikariPoolFactory.class);
        portForwarder = mock(TunnelPortForwarder.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        databaseQueryService = mock(DatabaseQueryService.class);
        service = new DataSourceServiceImpl(repo, mapper, crypto, poolFactory,
                portForwarder, currentUserProvider, databaseQueryService);

        when(repo.save(any(DataSourceConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(crypto.encrypt(any())).thenReturn("cipher-new");
    }

    @Test
    @DisplayName("只改备注:保留已有连接池,不打断进行中的查询")
    void keepsPoolWhenOnlyRemarkChanged() {
        DataSourceConfig existing = existingConfig();
        when(repo.findById(DS_ID)).thenReturn(Optional.of(existing));

        DataSourceRequest req = requestOf(existing);
        req.setRemark("换个备注");

        service.update(DS_ID, req);

        verify(databaseQueryService, never()).evict(anyLong());
    }

    @Test
    @DisplayName("改 host:丢弃连接池,下次查询按新地址重建")
    void evictsPoolWhenHostChanged() {
        DataSourceConfig existing = existingConfig();
        when(repo.findById(DS_ID)).thenReturn(Optional.of(existing));

        DataSourceRequest req = requestOf(existing);
        req.setHost("10.0.0.2");

        service.update(DS_ID, req);

        verify(databaseQueryService).evict(DS_ID);
    }

    @Test
    @DisplayName("改端口:丢弃连接池")
    void evictsPoolWhenPortChanged() {
        DataSourceConfig existing = existingConfig();
        when(repo.findById(DS_ID)).thenReturn(Optional.of(existing));

        DataSourceRequest req = requestOf(existing);
        req.setPort(3307);

        service.update(DS_ID, req);

        verify(databaseQueryService).evict(DS_ID);
    }

    @Test
    @DisplayName("改密码:丢弃连接池,旧凭据不再被复用")
    void evictsPoolWhenPasswordChanged() {
        DataSourceConfig existing = existingConfig();
        when(repo.findById(DS_ID)).thenReturn(Optional.of(existing));

        DataSourceRequest req = requestOf(existing);
        req.setPassword("brand-new-secret");

        service.update(DS_ID, req);

        verify(databaseQueryService).evict(DS_ID);
    }

    @Test
    @DisplayName("改隧道绑定:丢弃连接池,避免继续走旧隧道端口")
    void evictsPoolWhenTunnelChanged() {
        DataSourceConfig existing = existingConfig();
        when(repo.findById(DS_ID)).thenReturn(Optional.of(existing));

        DataSourceRequest req = requestOf(existing);
        req.setTunnelBastionId(9L);

        service.update(DS_ID, req);

        verify(databaseQueryService).evict(DS_ID);
    }

    @Test
    @DisplayName("密码留空:视为不修改,不应误判成连接参数变化")
    void emptyPasswordIsNotTreatedAsChange() {
        DataSourceConfig existing = existingConfig();
        when(repo.findById(DS_ID)).thenReturn(Optional.of(existing));

        DataSourceRequest req = requestOf(existing);
        req.setPassword(null);

        service.update(DS_ID, req);

        verify(databaseQueryService, never()).evict(anyLong());
    }

    @Test
    @DisplayName("删除数据源:连接池与隧道端口一并回收")
    void deleteEvictsPool() {
        DataSourceConfig existing = existingConfig();
        when(repo.findById(DS_ID)).thenReturn(Optional.of(existing));

        service.delete(DS_ID);

        verify(databaseQueryService).evict(DS_ID);
        verify(repo).delete(existing);
    }

    // ------------------------------------------------------------------

    private static DataSourceConfig existingConfig() {
        DataSourceConfig c = new DataSourceConfig();
        c.setId(DS_ID);
        c.setName("订单库");
        c.setDbType("MYSQL");
        c.setHost("10.0.0.1");
        c.setPort(3306);
        c.setDatabaseName("order_db");
        c.setUsername("root");
        c.setPasswordCipher("cipher-old");
        c.setRemark("旧备注");
        return c;
    }

    /** 构造一个与原配置完全一致的请求,便于只对单个字段制造差异 */
    private static DataSourceRequest requestOf(DataSourceConfig c) {
        DataSourceRequest r = new DataSourceRequest();
        r.setName(c.getName());
        r.setDbType(c.getDbType());
        r.setHost(c.getHost());
        r.setPort(c.getPort());
        r.setDatabaseName(c.getDatabaseName());
        r.setUsername(c.getUsername());
        r.setTunnelBastionId(c.getTunnelBastionId());
        r.setRemark(c.getRemark());
        return r;
    }
}
