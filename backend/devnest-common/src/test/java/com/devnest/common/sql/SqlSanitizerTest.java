package com.devnest.common.sql;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * SqlSanitizer 单元测试,与当前"黑名单"实现保持一致.
 *
 * 背景:实现已从"白名单模式"(只放行 SELECT/SHOW/DESC/DESCRIBE/EXPLAIN 开头)
 * 改为"黑名单模式"(放行 DML/DDL,仅拦截系统级危险操作).
 * 因此本测试不再断言 UPDATE/DROP/UNION/SET/information_schema 被拦截,
 * 而是明确断言它们放行 —— 既记录当前设计决策,也能在将来有人改回白名单时第一时间报警.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
class SqlSanitizerTest {

    private static void assertAllowed(String sql) {
        Assertions.assertDoesNotThrow(() -> SqlSanitizer.sanitize(sql), "应放行: " + sql);
    }

    private static void assertBlocked(String sql) {
        BizException ex = Assertions.assertThrows(BizException.class,
                () -> SqlSanitizer.sanitize(sql), "应被拦截: " + sql);
        Assertions.assertEquals(ErrorCode.SQL_BLOCKED_BY_BLACKLIST.code(), ex.getCode(),
                "错误码不匹配: " + sql);
    }

    // -------- 放行:常规查询 --------
    @Test
    void allowNormalSelect() {
        String sql = "SELECT id, name FROM users WHERE status = 1";
        Assertions.assertEquals(sql, SqlSanitizer.sanitize(sql));
    }

    @Test
    void allowShowDescribeExplain() {
        assertAllowed("SHOW DATABASES");
        assertAllowed("SHOW TABLES");
        assertAllowed("DESC user_info");
        assertAllowed("DESCRIBE user_info");
        assertAllowed("EXPLAIN SELECT * FROM users WHERE id=1");
        assertAllowed("WITH t AS (SELECT 1) SELECT * FROM t");
    }

    // -------- 放行:DML / DDL(黑名单模式的设计决策,不是漏检) --------
    @Test
    void allowDmlByDesign() {
        assertAllowed("UPDATE users SET name='x' WHERE id=1");
        assertAllowed("DELETE FROM users WHERE id=1");
        assertAllowed("INSERT INTO logs(id, msg) VALUES(1, 'a')");
    }

    @Test
    void allowDdlByDesign() {
        assertAllowed("DROP TABLE users");
        assertAllowed("ALTER TABLE users ADD COLUMN age INT");
        assertAllowed("TRUNCATE TABLE users");
        assertAllowed("CREATE TABLE t2 LIKE t1");
    }

    @Test
    void allowSetUnionAndSensitiveSchemaByDesign() {
        assertAllowed("SET @@global.max_connections=1000");
        assertAllowed("SELECT a FROM t1 UNION ALL SELECT b FROM t2");
        assertAllowed("SELECT * FROM information_schema.tables");
        assertAllowed("SELECT host,user FROM mysql.user");
    }

    @Test
    void allowStringLiteralContainingInnocentWords() {
        assertAllowed("SELECT * FROM logs WHERE action='delete'");
        assertAllowed("SELECT * FROM logs WHERE action='drop'");
        assertAllowed("SELECT 'a;b;c' AS tag FROM t");
    }

    // -------- 放行:注释剥离 --------
    @Test
    void allowLeadingInlineCommentThenSelect() {
        String out = SqlSanitizer.sanitize("/* hint */ SELECT * FROM t");
        Assertions.assertTrue(out.toUpperCase().startsWith("SELECT"));
    }

    @Test
    void stripAllCommentStyles() {
        String raw = "SELECT a, /* inline */ b FROM t -- end\nWHERE c = 1 # tail";
        String out = SqlSanitizer.stripComments(raw);
        Assertions.assertFalse(out.contains("/*"), "残留块注释");
        Assertions.assertFalse(out.contains("--"), "残留行注释");
        Assertions.assertFalse(out.contains("#"), "残留 # 注释");
    }

    @Test
    void commentOnlySqlIsRejected() {
        BizException ex = Assertions.assertThrows(BizException.class,
                () -> SqlSanitizer.sanitize("/* only comment */"));
        Assertions.assertEquals(ErrorCode.PARAM_INVALID.code(), ex.getCode());
    }

    // -------- 拦截:文件系统操作 --------
    @Test
    void rejectIntoOutfile() {
        assertBlocked("SELECT * FROM users INTO OUTFILE '/tmp/a.txt'");
    }

    @Test
    void rejectIntoDumpfile() {
        assertBlocked("SELECT * FROM users INTO DUMPFILE '/tmp/a.bin'");
    }

    @Test
    void rejectLoadFile() {
        assertBlocked("SELECT LOAD_FILE('/etc/passwd')");
    }

    @Test
    void rejectLoadData() {
        assertBlocked("LOAD DATA INFILE '/tmp/a.csv' INTO TABLE t");
    }

    // -------- 拦截:盲注探测 --------
    @Test
    void rejectSleep() {
        assertBlocked("SELECT SLEEP(5) FROM dual");
    }

    @Test
    void rejectBenchmark() {
        assertBlocked("SELECT BENCHMARK(1000000, MD5('a'))");
    }

    @Test
    void rejectPgSleep() {
        assertBlocked("SELECT PG_SLEEP(5)");
    }

    @Test
    void rejectWaitforDelay() {
        assertBlocked("SELECT 1; WAITFOR DELAY '0:0:5'");
    }

    // -------- 拦截:动态执行 --------
    @Test
    void rejectExec() {
        assertBlocked("SELECT * FROM t WHERE 1=1 OR EXEC xp_cmdshell('dir')");
    }

    @Test
    void rejectExecute() {
        assertBlocked("EXECUTE stmt");
    }

    @Test
    void rejectPrepare() {
        assertBlocked("PREPARE stmt FROM 'SELECT 1'");
    }

    @Test
    void rejectDeallocate() {
        assertBlocked("DEALLOCATE PREPARE stmt");
    }

    // -------- 拦截:大小写 / 空白 / 注释拆分等绕过手法 --------
    @Test
    void rejectCaseInsensitive() {
        assertBlocked("select * from users into outfile '/tmp/a.txt'");
        assertBlocked("select sleep(1)");
    }

    @Test
    void rejectExtraWhitespaceBypass() {
        assertBlocked("SELECT * FROM users INTO    OUTFILE '/tmp/a.txt'");
        assertBlocked("SELECT * FROM users INTO\tOUTFILE '/tmp/a.txt'");
        assertBlocked("LOAD\nDATA INFILE '/tmp/a.csv' INTO TABLE t");
    }

    @Test
    void rejectParenSpacingBypass() {
        assertBlocked("SELECT SLEEP (5)");
        assertBlocked("SELECT BENCHMARK (100, MD5('a'))");
    }

    @Test
    void rejectCommentSplitBypass() {
        assertBlocked("SELECT * FROM users INTO/**/OUTFILE '/tmp/a.txt'");
        assertBlocked("SELECT SLEEP/**/(5)");
        assertBlocked("SELECT 1 # comment\n INTO OUTFILE '/tmp/a.txt'");
    }

    // -------- 参数边界:空 / 超长 --------
    @Test
    void rejectNullAndBlankSql() {
        BizException e1 = Assertions.assertThrows(BizException.class,
                () -> SqlSanitizer.sanitize(null));
        Assertions.assertEquals(ErrorCode.PARAM_INVALID.code(), e1.getCode());
        BizException e2 = Assertions.assertThrows(BizException.class,
                () -> SqlSanitizer.sanitize("   "));
        Assertions.assertEquals(ErrorCode.PARAM_INVALID.code(), e2.getCode());
        BizException e3 = Assertions.assertThrows(BizException.class,
                () -> SqlSanitizer.sanitize(""));
        Assertions.assertEquals(ErrorCode.PARAM_INVALID.code(), e3.getCode());
    }

    @Test
    void rejectOverlengthSql() {
        // 实现上限为 50000 字符(旧测试按 11000 断言,已与实现脱节)
        String sql = "SELECT " + "1".repeat(50_000);
        BizException ex = Assertions.assertThrows(BizException.class,
                () -> SqlSanitizer.sanitize(sql));
        Assertions.assertEquals(ErrorCode.SQL_RESULT_TOO_LARGE.code(), ex.getCode());
    }

    @Test
    void allowSqlExactlyAtLengthLimit() {
        String sql = "SELECT " + "1".repeat(50_000 - "SELECT ".length());
        Assertions.assertEquals(50_000, sql.length());
        Assertions.assertDoesNotThrow(() -> SqlSanitizer.sanitize(sql));
    }

    // -------- 多语句分割 --------
    @Test
    void splitMultipleStatements() {
        List<String> list = SqlSanitizer.splitAndSanitize("SELECT 1; SELECT 2");
        Assertions.assertEquals(List.of("SELECT 1", "SELECT 2"), list);
    }

    @Test
    void splitKeepsSemicolonInsideQuotes() {
        Assertions.assertEquals(1, SqlSanitizer.splitAndSanitize("SELECT 'a;b;c' FROM t").size());
        Assertions.assertEquals(1, SqlSanitizer.splitAndSanitize("SELECT \"a;b\" FROM t").size());
        Assertions.assertEquals(1, SqlSanitizer.splitAndSanitize("SELECT `a;b` FROM t").size());
    }

    @Test
    void splitSkipsEmptyStatements() {
        Assertions.assertEquals(List.of("SELECT 1"), SqlSanitizer.splitAndSanitize("SELECT 1;;   "));
        Assertions.assertEquals(List.of(), SqlSanitizer.splitAndSanitize("   "));
        Assertions.assertEquals(List.of(), SqlSanitizer.splitAndSanitize(null));
    }

    @Test
    void splitAllowsDmlDdlByDesign() {
        // 多语句本身不再拦截,只拦危险操作(DROP 属于允许的 DDL)
        Assertions.assertEquals(2, SqlSanitizer.splitAndSanitize("SELECT 1; DROP TABLE t").size());
    }

    @Test
    void splitRejectsWhenAnyStatementIsDangerous() {
        BizException ex = Assertions.assertThrows(BizException.class,
                () -> SqlSanitizer.splitAndSanitize("SELECT 1; SELECT LOAD_FILE('/etc/passwd')"));
        Assertions.assertEquals(ErrorCode.SQL_BLOCKED_BY_BLACKLIST.code(), ex.getCode());
    }

    @Test
    void splitStripsCommentsPerStatement() {
        List<String> list = SqlSanitizer.splitAndSanitize("SELECT 1; -- comment\nSELECT 2");
        Assertions.assertEquals(List.of("SELECT 1", "SELECT 2"), list);
    }

    // -------- splitBySemicolon 回归 --------
    @Test
    void splitBySemicolonBasics() {
        Assertions.assertEquals(List.of("SELECT 1", "SELECT 2"),
                SqlSanitizer.splitBySemicolon("SELECT 1; SELECT 2"));
        Assertions.assertEquals(List.of("SELECT 1"),
                SqlSanitizer.splitBySemicolon("SELECT 1;"));
    }
}
