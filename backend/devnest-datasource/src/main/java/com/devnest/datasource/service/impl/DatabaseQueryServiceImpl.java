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
 * 支持 MySQL / 达梦 DM8:在 SQL 方言 / Schema 元数据 / 标识符引号 上有差异,统一按 dbType 分支.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Service
@RequiredArgsConstructor
public class DatabaseQueryServiceImpl implements DatabaseQueryService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryServiceImpl.class);

    // MySQL 内置库过滤
    private static final Set<String> MYSQL_SYS_DATABASES = Set.of(
            "information_schema", "mysql", "performance_schema", "sys");
    // 达梦内置用户(Schema)过滤 - 常见系统用户
    private static final Set<String> DM_SYS_SCHEMAS = Set.of(
            "SYS", "SYSDBA", "SYSSFADMIN", "SYSSSO", "SYSDBG",
            "SYSJE", "CTISYS", "SIUDBA", "SYSAUDITOR", "SYSDWR");

    private final DataSourceConfigRepository repo;
    private final SqlExecutionLogRepository logRepo;
    private final DataSourceMapper mapper;
    private final CryptoService crypto;
    private final HikariPoolFactory poolFactory;
    private final TunnelPortForwarder portForwarder;

    /** datasourceId → 运行时连接上下文 */
    private final Map<Long, ConnContext> connMap = new ConcurrentHashMap<>();
    private record ConnContext(HikariDataSource pool, Integer tunnelLocalPort) {}

    // ==================== Schema 元数据 ====================

    @Override
    public List<SchemaNode> getSchemaTree(Long datasourceId) {
        DataSourceConfig ds = findConfig(datasourceId);
        ConnContext ctx = getOrCreateContext(ds);
        boolean isDm = "DM".equalsIgnoreCase(ds.getDbType());
        try (Connection conn = ctx.pool.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            List<SchemaNode> result = new ArrayList<>();
            if (isDm) {
                result.addAll(loadDmSchemaTree(conn, meta, ds));
            } else {
                result.addAll(loadMysqlSchemaTree(conn, meta, ds));
            }
            return result;
        } catch (SQLException e) {
            throw new BizException(ErrorCode.SQL_EXECUTE_FAILED, e.getMessage());
        }
    }

    /** MySQL: Catalog = 库 */
    private List<SchemaNode> loadMysqlSchemaTree(Connection conn, DatabaseMetaData meta,
                                                 DataSourceConfig ds) throws SQLException {
        String configuredDb = ds.getDatabaseName();
        List<String> dbNames = new ArrayList<>();
        if (configuredDb != null && !configuredDb.isBlank()) {
            dbNames.add(configuredDb);
        } else {
            try (ResultSet rs = meta.getCatalogs()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_CAT");
                    if (!MYSQL_SYS_DATABASES.contains(name)) dbNames.add(name);
                }
            }
        }
        List<SchemaNode> databases = new ArrayList<>();
        for (String dbName : dbNames) {
            SchemaNode dbNode = new SchemaNode();
            dbNode.setName(dbName);
            dbNode.setType("DATABASE");
            fillTablesMysql(meta, dbName, dbNode);
            databases.add(dbNode);
        }
        return databases;
    }

    /** DM: Schema = 用户;DM 没有 catalog 概念,一个库下多个用户模式 */
    private List<SchemaNode> loadDmSchemaTree(Connection conn, DatabaseMetaData meta,
                                              DataSourceConfig ds) throws SQLException {
        String configured = ds.getDatabaseName(); // 可填模式名(用户)或库名(DM库只有一个)
        List<String> schemaList = new ArrayList<>();
        if (configured != null && !configured.isBlank()
                && !DM_SYS_SCHEMAS.contains(configured.toUpperCase())) {
            schemaList.add(configured);
        } else {
            try (ResultSet rs = meta.getSchemas()) {
                while (rs.next()) {
                    String schemaName = rs.getString("TABLE_SCHEM");
                    if (!DM_SYS_SCHEMAS.contains(schemaName)) {
                        schemaList.add(schemaName);
                    }
                }
            }
            // 如果没拿到任何模式,回退:用 CURRENT USER 作为默认 schema
            if (schemaList.isEmpty()) {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT USER FROM DUAL")) {
                    if (rs.next()) schemaList.add(rs.getString(1));
                } catch (SQLException ignored) {}
            }
        }
        List<SchemaNode> databases = new ArrayList<>();
        for (String schemaName : schemaList) {
            SchemaNode dbNode = new SchemaNode();
            dbNode.setName(schemaName);
            dbNode.setType("DATABASE");
            fillTablesDm(meta, schemaName, dbNode);
            databases.add(dbNode);
        }
        return databases;
    }

    // --- MySQL: catalog 语义 ---
    private void fillTablesMysql(DatabaseMetaData meta, String catalog, SchemaNode dbNode) throws SQLException {
        try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                SchemaNode tableNode = new SchemaNode();
                tableNode.setName(rs.getString("TABLE_NAME"));
                tableNode.setType("TABLE");
                tableNode.setRemark(rs.getString("REMARKS"));
                fillColumns(meta, catalog, null, tableNode);
                dbNode.getChildren().add(tableNode);
            }
        }
        try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"VIEW"})) {
            while (rs.next()) {
                SchemaNode viewNode = new SchemaNode();
                viewNode.setName(rs.getString("TABLE_NAME"));
                viewNode.setType("VIEW");
                viewNode.setRemark(rs.getString("REMARKS"));
                fillColumns(meta, catalog, null, viewNode);
                dbNode.getChildren().add(viewNode);
            }
        }
    }

    // --- DM: schema 语义,没有 catalog ---
    private void fillTablesDm(DatabaseMetaData meta, String schema, SchemaNode dbNode) throws SQLException {
        try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                SchemaNode tableNode = new SchemaNode();
                tableNode.setName(rs.getString("TABLE_NAME"));
                tableNode.setType("TABLE");
                tableNode.setRemark(rs.getString("REMARKS"));
                fillColumns(meta, null, schema, tableNode);
                dbNode.getChildren().add(tableNode);
            }
        }
        try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"VIEW"})) {
            while (rs.next()) {
                SchemaNode viewNode = new SchemaNode();
                viewNode.setName(rs.getString("TABLE_NAME"));
                viewNode.setType("VIEW");
                viewNode.setRemark(rs.getString("REMARKS"));
                fillColumns(meta, null, schema, viewNode);
                dbNode.getChildren().add(viewNode);
            }
        }
    }

    private void fillColumns(DatabaseMetaData meta, String catalog, String schemaPattern, SchemaNode tableNode) throws SQLException {
        try (ResultSet cols = meta.getColumns(catalog, schemaPattern, tableNode.getName(), "%")) {
            while (cols.next()) {
                SchemaNode col = new SchemaNode();
                col.setName(cols.getString("COLUMN_NAME"));
                col.setType("COLUMN");
                col.setDataType(cols.getString("TYPE_NAME"));
                col.setRemark(cols.getString("REMARKS"));
                tableNode.getChildren().add(col);
            }
        }
        try (ResultSet pks = meta.getPrimaryKeys(catalog, schemaPattern, tableNode.getName())) {
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
        DataSourceConfig ds = findConfig(datasourceId);
        boolean isDm = "DM".equalsIgnoreCase(ds.getDbType());
        int offset = page * size;

        // 标识符引用:MySQL 反引号 / DM 双引号
        String q = isDm ? "\"" : "`";
        String tableRef;
        if (database != null && !database.isBlank()) {
            tableRef = q + database + q + "." + q + tableName + q;
        } else {
            tableRef = q + tableName + q;
        }

        // 分页:MySQL 用 LIMIT offset,size;DM 支持 LIMIT size OFFSET offset (SQL标准)
        String sql = isDm
                ? "SELECT * FROM " + tableRef + " LIMIT " + size + " OFFSET " + offset
                : "SELECT * FROM " + tableRef + " LIMIT " + offset + ", " + size;

        ConnContext ctx = getOrCreateContext(ds);
        TableDataResult result;
        try (Connection conn = ctx.pool.getConnection()) {
            // 数据查询 + 列注释 + 行数统计复用同一连接,避免多次借还
            try (PreparedStatement ps = conn.prepareStatement(sql,
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                ps.setFetchSize(Math.min(size, 200));
                try (ResultSet rs = ps.executeQuery()) {
                    result = extractResult(rs, size);
                    String dbForComment = database != null && !database.isBlank() ? database
                            : (conn.getCatalog() == null ? "" : conn.getCatalog());
                    String schemaForComment = database;
                    if (isDm) {
                        schemaForComment = (dbForComment == null || dbForComment.isEmpty())
                                ? guessCurrentSchema(conn) : dbForComment;
                        dbForComment = null;
                    }
                    result.setColumnComments(loadColumnComments(
                            conn, isDm, dbForComment, schemaForComment, tableName, result.getColumns()));
                }
            }
            // 总数(复用同一连接)
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableRef)) {
                if (rs.next()) result.setTotal(rs.getLong(1));
            } catch (SQLException e) {
                log.warn("查询表行数失败: {}", e.getMessage());
            }
        } catch (SQLException e) {
            throw new BizException(ErrorCode.SQL_EXECUTE_FAILED, e.getMessage());
        }
        return result;
    }

    // ==================== SQL 执行(多语句) ====================

    @Override
    public MultiSqlResult executeSql(Long datasourceId, String sqlText, int maxRows) {
        List<String> sqlList = SqlSanitizer.splitAndSanitize(sqlText);
        if (sqlList.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SQL 为空");
        }
        DataSourceConfig ds = findConfig(datasourceId);
        ConnContext ctx = getOrCreateContext(ds);
        long totalStart = System.currentTimeMillis();
        List<SqlResultItem> items = new ArrayList<>(sqlList.size());
        boolean isDm = "DM".equalsIgnoreCase(ds.getDbType());

        // 先执行所有 SQL 并收集结果,再在连接释放后异步记录日志
        List<String> executedSqls = new ArrayList<>(sqlList.size());
        try (Connection conn = ctx.pool.getConnection()) {
            for (String sql : sqlList) {
                SqlResultItem item = executeSingle(conn, isDm, sql, maxRows);
                items.add(item);
                executedSqls.add(sql);
            }
        } catch (SQLException e) {
            throw new BizException(ErrorCode.SQL_EXECUTE_FAILED, e.getMessage());
        }
        // 日志记录在连接释放后执行,避免长时间持有数据源连接
        for (int i = 0; i < executedSqls.size(); i++) {
            logExecution(datasourceId, ds.getName(), executedSqls.get(i), items.get(i));
        }

        MultiSqlResult result = new MultiSqlResult();
        result.setResults(items);
        result.setTotalCostMs(System.currentTimeMillis() - totalStart);
        return result;
    }

    private SqlResultItem executeSingle(Connection conn, boolean isDm, String sql, int maxRows) {
        SqlResultItem item = new SqlResultItem();
        item.setSql(sql);
        long start = System.currentTimeMillis();
        try {
            String upper = sql.toUpperCase().trim();
            if (upper.startsWith("SELECT") || upper.startsWith("SHOW")
                    || upper.startsWith("DESCRIBE") || upper.startsWith("DESC")
                    || upper.startsWith("EXPLAIN") || upper.startsWith("WITH")) {
                try (PreparedStatement ps = conn.prepareStatement(sql,
                        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                    ps.setFetchSize(Math.min(maxRows, 200));
                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        List<String> cols = extractColumns(rs);
                        item.setColumns(cols);
                        item.setRows(extractRows(rs, maxRows));
                        if (!cols.isEmpty()) {
                            String cat = conn.getCatalog();
                            String schemaForComment = null;
                            String catForComment = null;
                            if (isDm) {
                                // DM: schema = TABLE_SCHEM
                                schemaForComment = meta.getTableName(1);
                                if (schemaForComment != null && !schemaForComment.isEmpty()) {
                                    // tableName 本身已足够 getColumns
                                    schemaForComment = guessCurrentSchema(conn);
                                } else {
                                    schemaForComment = guessCurrentSchema(conn);
                                }
                            } else {
                                catForComment = (cat == null || cat.isEmpty()) ? meta.getTableName(1) : cat;
                                if (catForComment == null || catForComment.isEmpty()) {
                                    catForComment = cat;
                                }
                            }
                            String tbl = meta.getTableName(1);
                            if (tbl != null && !tbl.isEmpty()) {
                                item.setColumnComments(loadColumnComments(
                                        conn, isDm, catForComment, schemaForComment, tbl, cols));
                            }
                        }
                    }
                }
            } else {
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

    // ==================== ResultSet 提取 ====================

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
                row.put(columns.get(i - 1), sanitizeValue(rs.getObject(i), meta, i));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 清洗 JDBC 值,确保 Jackson 可安全序列化.
     * 问题场景:DM/Oracle 等驱动 getObject() 返回驱动内部对象(如 DmProperties),
     * 内部存在循环引用 → Jackson StackOverflowError.
     * 策略:只保留标准 Java 类型(String/Number/Boolean/Date/byte[]),其余一律 toString().
     */
    private static Object sanitizeValue(Object val, ResultSetMetaData meta, int colIdx) {
        if (val == null) return null;
        // 标准 JSON 可序列化类型直接返回
        if (val instanceof String || val instanceof Number || val instanceof Boolean
                || val instanceof java.util.Date || val instanceof byte[]) {
            return val;
        }
        // CLOB / BLOB / SQL XML 等特殊 JDBC 类型
        if (val instanceof java.sql.Clob clob) {
            try { return clob.getSubString(1, (int) clob.length()); }
            catch (Exception e) { return val.toString(); }
        }
        if (val instanceof java.sql.Blob blob) {
            try { return blob.getBytes(1, (int) blob.length()); }
            catch (Exception e) { return val.toString(); }
        }
        if (val instanceof java.sql.SQLXML xml) {
            try { return xml.getString(); }
            catch (Exception e) { return val.toString(); }
        }
        // 驱动内部对象(如 DM 的 DmProperties / EPGroup)→ 转字符串避免循环引用
        String className = val.getClass().getName();
        if (className.startsWith("dm.") || className.startsWith("oracle.")
                || className.startsWith("com.mysql.cj.") && !className.contains("jdbc")) {
            return val.toString();
        }
        // 兜底:非标准类型全部 toString
        return val.toString();
    }

    /**
     * 加载列注释.
     * 策略:先用 JDBC getColumns (DatabaseMetaData) 取 REMARKS.
     * 如果全部为空,MySQL 再从 INFORMATION_SCHEMA.COLUMNS 补;
     * DM 则从 ALL_COL_COMMENTS 补(达梦专门的注释视图).
     */
    private List<String> loadColumnComments(Connection conn, boolean isDm,
                                            String catalog, String schema,
                                            String table, List<String> columns) {
        List<String> comments = new ArrayList<>();
        if (columns == null || columns.isEmpty() || table == null || table.isEmpty()) {
            for (int i = 0; i < (columns == null ? 0 : columns.size()); i++) comments.add(null);
            return comments;
        }
        Map<String, String> map = new HashMap<>(columns.size());
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(catalog, schema, table, "%")) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    String remark = rs.getString("REMARKS");
                    if (col != null) map.put(col, remark);
                }
            }
            // JDBC REMARKS 全为空:退化到数据库专有字典表
            boolean allEmpty = map.values().stream().allMatch(v -> v == null || v.isEmpty());
            if (allEmpty) {
                if (isDm) {
                    // DM 注释视图:ALL_TAB_COLUMNS (带 COMMENTS 视图 ALL_COL_COMMENTS)
                    String ownerFilter = (schema != null && !schema.isEmpty()) ? schema : guessCurrentSchema(conn);
                    String sql = "SELECT COLUMN_NAME, COMMENTS FROM ALL_COL_COMMENTS "
                            + "WHERE OWNER = ? AND TABLE_NAME = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, ownerFilter);
                        ps.setString(2, table.toUpperCase()); // DM 字典表默认大写
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String col = rs.getString(1);
                                String cm = rs.getString(2);
                                if (col != null && cm != null && !cm.isEmpty()) {
                                    map.put(col, cm);
                                }
                            }
                        }
                    } catch (SQLException ex) {
                        // 兼容模式:USER_TAB_COLUMNS 直接查
                        try (Statement st = conn.createStatement();
                             ResultSet rs = st.executeQuery(
                                     "SELECT COLUMN_NAME, DATA_TYPE FROM USER_TAB_COLUMNS "
                                     + "WHERE TABLE_NAME = '" + table.toUpperCase() + "'")) {
                            while (rs.next()) { /* 仅占位,不额外写 */ }
                        } catch (SQLException ignore) {}
                    }
                } else {
                    // MySQL: INFORMATION_SCHEMA.COLUMNS
                    String query = "SELECT COLUMN_NAME, COLUMN_COMMENT "
                            + "FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
                    try (PreparedStatement ps = conn.prepareStatement(query)) {
                        ps.setString(1, catalog != null ? catalog : conn.getCatalog());
                        ps.setString(2, table);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String col = rs.getString(1);
                                String cm = rs.getString(2);
                                if (col != null && cm != null && !cm.isEmpty()) {
                                    map.put(col, cm);
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("加载列注释失败: {}", e.getMessage());
        }
        for (String c : columns) comments.add(map.get(c));
        return comments;
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
            // 达梦 JDBC 标准格式:jdbc:dm://host:port/SCHEMA?schema=SCHEMA
            // schema 参数可选:默认连 SYS 时需指定 schemaName
            String url = "jdbc:dm://" + host + ":" + port;
            if (!name.isEmpty()) {
                url += "/" + name + "?schema=" + name;
            }
            return url;
        }
        throw new BizException(ErrorCode.DATASOURCE_UNSUPPORTED_TYPE, dbType);
    }

    private DataSourceConfig findConfig(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.DATASOURCE_NOT_FOUND));
    }

    /** 尝试从连接中推导当前 Schema(DM 用,MySQL 一般直接 getCatalog 即可) */
    private static String guessCurrentSchema(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT USER FROM DUAL")) {
            if (rs.next()) {
                String user = rs.getString(1);
                return user == null ? "SYSDBA" : user.toUpperCase();
            }
        } catch (SQLException ignored) {}
        return "SYSDBA";
    }
}
