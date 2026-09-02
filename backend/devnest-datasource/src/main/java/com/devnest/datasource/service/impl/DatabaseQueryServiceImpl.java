package com.devnest.datasource.service.impl;

import com.devnest.common.crypto.CryptoService;
import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.common.sql.SqlSanitizer;
import com.devnest.core.pool.HikariPoolFactory;
import com.devnest.core.spi.TunnelPortForwarder;
import com.devnest.datasource.dto.SchemaNode;
import com.devnest.datasource.dto.SqlLogDto;
import com.devnest.datasource.dto.TableDataResult;
import com.devnest.datasource.entity.DataSourceConfig;
import com.devnest.datasource.entity.SqlExecutionLog;
import com.devnest.datasource.mapper.DataSourceMapper;
import com.devnest.datasource.repository.DataSourceConfigRepository;
import com.devnest.datasource.repository.SqlExecutionLogRepository;
import com.devnest.datasource.service.DatabaseQueryService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库查询服务实现.
 * 管理 数据源ID → HikariPool + 隧道端口 的映射,复用连接.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Service
@RequiredArgsConstructor
public class DatabaseQueryServiceImpl implements DatabaseQueryService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryServiceImpl.class);

    private final DataSourceConfigRepository repo;
    private final SqlExecutionLogRepository logRepo;
    private final DataSourceMapper mapper;
    private final CryptoService crypto;
    private final HikariPoolFactory poolFactory;
    private final TunnelPortForwarder portForwarder;

    /** datasourceId → 运行时连接上下文(连接池 + 隧道端口) */
    private final Map<Long, ConnContext> connMap = new ConcurrentHashMap<>();

    private record ConnContext(HikariDataSource pool, Integer tunnelLocalPort) {}

    // ==================== Schema 元数据 ====================

    @Override
    public List<SchemaNode> getSchemaTree(Long datasourceId) {
        DataSourceConfig ds = findConfig(datasourceId);
        ConnContext ctx = getOrCreateContext(ds);
        try (Connection conn = ctx.pool.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            List<SchemaNode> tables = new ArrayList<>();
            // 查表
            try (ResultSet rs = meta.getTables(ds.getDatabaseName(), null, "%",
                    new String[]{"TABLE"})) {
                while (rs.next()) {
                    SchemaNode tableNode = new SchemaNode();
                    tableNode.setName(rs.getString("TABLE_NAME"));
                    tableNode.setType("TABLE");
                    tableNode.setRemark(rs.getString("REMARKS"));
                    fillColumns(meta, ds.getDatabaseName(), tableNode);
                    tables.add(tableNode);
                }
            }
            // 查视图
            try (ResultSet rs = meta.getTables(ds.getDatabaseName(), null, "%",
                    new String[]{"VIEW"})) {
                while (rs.next()) {
                    SchemaNode viewNode = new SchemaNode();
                    viewNode.setName(rs.getString("TABLE_NAME"));
                    viewNode.setType("VIEW");
                    viewNode.setRemark(rs.getString("REMARKS"));
                    fillColumns(meta, ds.getDatabaseName(), viewNode);
                    tables.add(viewNode);
                }
            }
            return tables;
        } catch (SQLException e) {
            throw new BizException(ErrorCode.SQL_EXECUTE_FAILED, e.getMessage());
        }
    }

    private void fillColumns(DatabaseMetaData meta, String catalog, SchemaNode tableNode)
            throws SQLException {
        try (ResultSet cols = meta.getColumns(catalog, null, tableNode.getName(), "%")) {
            while (cols.next()) {
                SchemaNode col = new SchemaNode();
                col.setName(cols.getString("COLUMN_NAME"));
                col.setType("COLUMN");
                col.setDataType(cols.getString("TYPE_NAME"));
                col.setRemark(cols.getString("REMARKS"));
                tableNode.getChildren().add(col);
            }
        }
        // 标记主键
        try (ResultSet pks = meta.getPrimaryKeys(catalog, null, tableNode.getName())) {
            while (pks.next()) {
                String pkCol = pks.getString("COLUMN_NAME");
                tableNode.getChildren().stream()
                        .filter(c -> c.getName().equals(pkCol))
                        .findFirst()
                        .ifPresent(c -> c.setPrimaryKey(true));
            }
        }
    }

    // ==================== 数据预览 ====================

    @Override
    public TableDataResult previewTable(Long datasourceId, String tableName, int page, int size) {
        int offset = page * size;
        // 使用子查询分页(MySQL 通用),兼容性最好
        String sql = "SELECT * FROM `" + tableName + "` LIMIT " + offset + ", " + size;
        // previewTable 的 SQL 是内部生成的,不需要走白名单
        TableDataResult result = executeRaw(datasourceId, sql, size);
        // 查总数
        try (Connection conn = getOrCreateContext(findConfig(datasourceId)).pool.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM `" + tableName + "`")) {
            if (rs.next()) result.setTotal(rs.getLong(1));
        } catch (SQLException e) {
            log.warn("查询表行数失败: {}", e.getMessage());
        }
        return result;
    }

    // ==================== SQL 执行 ====================

    @Override
    public TableDataResult executeSql(Long datasourceId, String sql, int maxRows) {
        // 白名单校验
        String sanitized = SqlSanitizer.sanitize(sql);
        return executeRawWithLog(datasourceId, sanitized, maxRows);
    }

    private TableDataResult executeRawWithLog(Long datasourceId, String sql, int maxRows) {
        DataSourceConfig ds = findConfig(datasourceId);
        long start = System.currentTimeMillis();
        TableDataResult result;
        SqlExecutionLog logEntity = new SqlExecutionLog();
        logEntity.setDatasourceId(datasourceId);
        logEntity.setDatasourceName(ds.getName());
        logEntity.setSqlText(sql);

        try {
            result = executeRaw(datasourceId, sql, maxRows);
            long cost = System.currentTimeMillis() - start;
            result.setCostMs(cost);
            logEntity.setStatus("SUCCESS");
            logEntity.setCostMs(cost);
            logEntity.setRowCount(result.getRows() != null ? result.getRows().size() : 0);
        } catch (BizException e) {
            logEntity.setStatus("BLOCKED");
            logEntity.setErrorMsg(e.getMessage());
            logRepo.save(logEntity);
            throw e;
        } catch (Exception e) {
            logEntity.setStatus("FAILED");
            logEntity.setErrorMsg(e.getMessage());
            logRepo.save(logEntity);
            throw new BizException(ErrorCode.SQL_EXECUTE_FAILED, e.getMessage());
        }
        logRepo.save(logEntity);
        return result;
    }

    private TableDataResult executeRaw(Long datasourceId, String sql, int maxRows) {
        DataSourceConfig ds = findConfig(datasourceId);
        ConnContext ctx = getOrCreateContext(ds);
        try (Connection conn = ctx.pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            ps.setFetchSize(Math.min(maxRows, 200));
            try (ResultSet rs = ps.executeQuery()) {
                return extractResult(rs, maxRows);
            }
        } catch (SQLException e) {
            throw new BizException(ErrorCode.SQL_EXECUTE_FAILED, e.getMessage());
        }
    }

    private TableDataResult extractResult(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next() && rows.size() < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.put(columns.get(i - 1), rs.getObject(i));
            }
            rows.add(row);
        }
        TableDataResult result = new TableDataResult();
        result.setColumns(columns);
        result.setRows(rows);
        result.setTotal(rows.size());
        return result;
    }

    // ==================== SQL 日志 ====================

    @Override
    public Page<SqlLogDto> getSqlHistory(Long datasourceId, Pageable pageable) {
        return logRepo.findByDatasourceIdOrderByCreateTimeDesc(datasourceId, pageable)
                .map(mapper::toLogDto);
    }

    @Override
    public List<SqlLogDto> getRecentSql(Long datasourceId) {
        return logRepo.findTop20ByDatasourceIdOrderByCreateTimeDesc(datasourceId)
                .stream().map(mapper::toLogDto).toList();
    }

    // ==================== 连接上下文管理 ====================

    private ConnContext getOrCreateContext(DataSourceConfig ds) {
        return connMap.computeIfAbsent(ds.getId(), id -> {
            Integer tunnelPort = null;
            String host = ds.getHost();
            int port = ds.getPort();
            if (ds.getTunnelBastionId() != null) {
                if (!portForwarder.isBastionRunning(ds.getTunnelBastionId())) {
                    throw new BizException(ErrorCode.TUNNEL_NOT_RUNNING, "请先启动绑定的 SSH 隧道");
                }
                tunnelPort = portForwarder.allocateTunnel(
                        ds.getTunnelBastionId(), ds.getHost(), ds.getPort());
                host = "127.0.0.1";
                port = tunnelPort;
            }
            String jdbcUrl = buildJdbcUrl(ds.getDbType(), host, port, ds.getDatabaseName());
            String driver = DataSourceServiceImpl.resolveDriver(ds.getDbType());
            String password = ds.decryptPassword(crypto);
            String poolKey = DataSourceServiceImpl.poolKey(ds.getId());
            HikariDataSource pool = poolFactory.getOrCreate(poolKey, jdbcUrl, driver,
                    ds.getUsername(), password);
            return new ConnContext(pool, tunnelPort);
        });
    }

    private static String buildJdbcUrl(String dbType, String host, int port, String dbName) {
        if ("MYSQL".equalsIgnoreCase(dbType)) {
            return "jdbc:mysql://" + host + ":" + port + "/" + dbName
                    + "?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false";
        }
        if ("DM".equalsIgnoreCase(dbType)) {
            return "jdbc:dm://" + host + ":" + port + "/" + dbName;
        }
        throw new BizException(ErrorCode.DATASOURCE_UNSUPPORTED_TYPE, dbType);
    }

    private DataSourceConfig findConfig(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.DATASOURCE_NOT_FOUND));
    }
}
