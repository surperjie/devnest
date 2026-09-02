package com.devnest.datasource.service.impl;

import com.devnest.common.crypto.CryptoService;
import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.common.sql.SqlSanitizer;
import com.devnest.core.pool.HikariPoolFactory;
import com.devnest.core.spi.TunnelPortForwarder;
import com.devnest.datasource.dto.MultiSqlResult;
import com.devnest.datasource.dto.SchemaNode;
import com.devnest.datasource.dto.SqlResultItem;
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

    private static final Set<String> SYS_DATABASES = Set.of(
            "information_schema", "mysql", "performance_schema", "sys");

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
            // 如果配置了具体库名,只查该库;否则查所有库
            String configuredDb = ds.getDatabaseName();
            List<String> dbNames = new ArrayList<>();
            if (configuredDb != null && !configuredDb.isBlank()) {
                dbNames.add(configuredDb);
            } else {
                try (ResultSet rs = meta.getCatalogs()) {
                    while (rs.next()) {
                        String name = rs.getString("TABLE_CAT");
                        if (!SYS_DATABASES.contains(name)) {
                            dbNames.add(name);
                        }
                    }
                }
            }
            // 构建库→表→字段三层树
            List<SchemaNode> databases = new ArrayList<>();
            for (String dbName : dbNames) {
                SchemaNode dbNode = new SchemaNode();
                dbNode.setName(dbName);
                dbNode.setType("DATABASE");
                fillTables(meta, dbName, dbNode);
                databases.add(dbNode);
            }
            return databases;
        } catch (SQLException e) {
            throw new BizException(ErrorCode.SQL_EXECUTE_FAILED, e.getMessage());
        }
    }

    private void fillTables(DatabaseMetaData meta, String catalog, SchemaNode dbNode)
            throws SQLException {
        // 查表
        try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                SchemaNode tableNode = new SchemaNode();
                tableNode.setName(rs.getString("TABLE_NAME"));
                tableNode.setType("TABLE");
                tableNode.setRemark(rs.getString("REMARKS"));
                fillColumns(meta, catalog, tableNode);
                dbNode.getChildren().add(tableNode);
            }
        }
        // 查视图
        try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"VIEW"})) {
            while (rs.next()) {
                SchemaNode viewNode = new SchemaNode();
                viewNode.setName(rs.getString("TABLE_NAME"));
                viewNode.setType("VIEW");
                viewNode.setRemark(rs.getString("REMARKS"));
                fillColumns(meta, catalog, viewNode);
                dbNode.getChildren().add(viewNode);
            }
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
    public TableDataResult previewTable(Long datasourceId, String database, String tableName, int page, int size) {
        int offset = page * size;
        // 构建带库名前缀的表引用
        String tableRef = (database != null && !database.isBlank())
                ? "`" + database + "`.`" + tableName + "`"
                : "`" + tableName + "`";
        String sql = "SELECT * FROM " + tableRef + " LIMIT " + offset + ", " + size;
        // previewTable 内部生成 SQL,直接执行
        DataSourceConfig ds = findConfig(datasourceId);
        ConnContext ctx = getOrCreateContext(ds);
        TableDataResult result;
        try (Connection conn = ctx.pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            ps.setFetchSize(Math.min(size, 200));
            try (ResultSet rs = ps.executeQuery()) {
                result = extractResult(rs, size);
            }
        } catch (SQLException e) {
            throw new BizException(ErrorCode.SQL_EXECUTE_FAILED, e.getMessage());
        }
        // 查总数
        try (Connection conn = getOrCreateContext(findConfig(datasourceId)).pool.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableRef)) {
            if (rs.next()) result.setTotal(rs.getLong(1));
        } catch (SQLException e) {
            log.warn("查询表行数失败: {}", e.getMessage());
        }
        return result;
    }

    // ==================== SQL 执行(多语句) ====================

    @Override
    public MultiSqlResult executeSql(Long datasourceId, String sqlText, int maxRows) {
        // 黑名单校验 + 多语句分割
        List<String> sqlList = SqlSanitizer.splitAndSanitize(sqlText);
        if (sqlList.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SQL 为空");
        }
        DataSourceConfig ds = findConfig(datasourceId);
        ConnContext ctx = getOrCreateContext(ds);
        long totalStart = System.currentTimeMillis();
        List<SqlResultItem> items = new ArrayList<>(sqlList.size());

        try (Connection conn = ctx.pool.getConnection()) {
            for (String sql : sqlList) {
                SqlResultItem item = executeSingle(conn, sql, maxRows);
                items.add(item);
                // 记录日志
                logExecution(datasourceId, ds.getName(), sql, item);
            }
        } catch (SQLException e) {
            throw new BizException(ErrorCode.SQL_EXECUTE_FAILED, e.getMessage());
        }

        MultiSqlResult result = new MultiSqlResult();
        result.setResults(items);
        result.setTotalCostMs(System.currentTimeMillis() - totalStart);
        return result;
    }

    private SqlResultItem executeSingle(Connection conn, String sql, int maxRows) {
        SqlResultItem item = new SqlResultItem();
        item.setSql(sql);
        long start = System.currentTimeMillis();
        try {
            String upper = sql.toUpperCase().trim();
            if (upper.startsWith("SELECT") || upper.startsWith("SHOW")
                    || upper.startsWith("DESCRIBE") || upper.startsWith("DESC")
                    || upper.startsWith("EXPLAIN") || upper.startsWith("WITH")) {
                // 查询类:返回结果集
                try (PreparedStatement ps = conn.prepareStatement(sql,
                        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                    ps.setFetchSize(Math.min(maxRows, 200));
                    try (ResultSet rs = ps.executeQuery()) {
                        item.setColumns(extractColumns(rs));
                        item.setRows(extractRows(rs, maxRows));
                    }
                }
            } else {
                // 非查询类:执行并返回影响行数
                try (Statement st = conn.createStatement()) {
                    int affected = st.executeUpdate(sql);
                    item.setAffectedRows(affected);
                }
            }
            item.setStatus("SUCCESS");
            item.setCostMs(System.currentTimeMillis() - start);
        } catch (SQLException e) {
            item.setStatus("FAILED");
            item.setErrorMsg(e.getMessage());
            item.setCostMs(System.currentTimeMillis() - start);
        }
        return item;
    }

    private void logExecution(Long datasourceId, String dsName, String sql, SqlResultItem item) {
        try {
            SqlExecutionLog logEntity = new SqlExecutionLog();
            logEntity.setDatasourceId(datasourceId);
            logEntity.setDatasourceName(dsName);
            logEntity.setSqlText(sql);
            logEntity.setStatus(item.getStatus());
            logEntity.setErrorMsg(item.getErrorMsg());
            logEntity.setCostMs(item.getCostMs());
            logEntity.setRowCount(item.getRows() != null ? item.getRows().size() : item.getAffectedRows());
            logRepo.save(logEntity);
        } catch (Exception e) {
            log.warn("SQL 日志保存失败: {}", e.getMessage());
        }
    }

    private TableDataResult extractResult(ResultSet rs, int maxRows) throws SQLException {
        List<String> columns = extractColumns(rs);
        List<Map<String, Object>> rows = extractRows(rs, maxRows);
        TableDataResult result = new TableDataResult();
        result.setColumns(columns);
        result.setRows(rows);
        result.setTotal(rows.size());
        return result;
    }

    private List<String> extractColumns(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }
        return columns;
    }

    private List<Map<String, Object>> extractRows(ResultSet rs, int maxRows) throws SQLException {
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
        return rows;
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
        String name = (dbName == null || dbName.isBlank()) ? "" : dbName;
        if ("MYSQL".equalsIgnoreCase(dbType)) {
            return "jdbc:mysql://" + host + ":" + port + "/" + name
                    + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false";
        }
        if ("DM".equalsIgnoreCase(dbType)) {
            return "jdbc:dm://" + host + ":" + port + "/" + name;
        }
        throw new BizException(ErrorCode.DATASOURCE_UNSUPPORTED_TYPE, dbType);
    }

    private DataSourceConfig findConfig(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.DATASOURCE_NOT_FOUND));
    }
}
