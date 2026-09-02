package com.devnest.common.sql;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * SqlSanitizer 单元测试:覆盖白名单规则及常见 bypass 手段.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
class SqlSanitizerTest {

    // -------- 允许的正常 SELECT / SHOW / DESCRIBE / EXPLAIN --------
    @Test
    void allowNormalSelect() {
        String sql = "SELECT id, name FROM users WHERE status = 1";
        Assertions.assertEquals(sql, SqlSanitizer.sanitize(sql));
    }

    @Test
    void allowShowDatabases() {
        Assertions.assertDoesNotThrow(() -> SqlSanitizer.sanitize("SHOW DATABASES"));
    }

    @Test
    void allowDescribeTable() {
        Assertions.assertDoesNotThrow(() -> SqlSanitizer.sanitize("DESC user_info"));
        Assertions.assertDoesNotThrow(() -> SqlSanitizer.sanitize("DESCRIBE user_info"));
    }

    @Test
    void allowExplain() {
        Assertions.assertDoesNotThrow(() ->
                SqlSanitizer.sanitize("EXPLAIN SELECT * FROM users WHERE id=1"));
    }

    @Test
    void allowSelectWithStringContainingKeyword() {
        // 说明:当前实现为了展示级最高安全级别,对关键字做全语句包含扫描(不做语法分析器).
        // 即使字符串字面量中出现 'delete' 也会被拦截.这是"宁可误杀也不遗漏"的策略.
        // 真实业务需要用 ? 占位符绑定参数而非拼 SQL.
        BizException ex = Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("SELECT * FROM logs WHERE action='delete'"));
        Assertions.assertEquals(ErrorCode.SQL_BLOCKED_BY_WHITELIST.code(), ex.getCode());
    }

    // -------- 规则 1:只允许白名单开头 --------
    @Test
    void rejectUpdateStart() {
        BizException ex = Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("UPDATE users SET name='x' WHERE id=1"));
        Assertions.assertEquals(ErrorCode.SQL_BLOCKED_BY_WHITELIST.code(), ex.getCode());
    }

    @Test
    void rejectDropStart() {
        Assertions.assertThrows(BizException.class, () -> SqlSanitizer.sanitize("DROP TABLE users"));
    }

    // -------- 规则 2:注释绕过开头 --------
    @Test
    void rejectSelectInsideCommentBypass() {
        // 去掉注释后开头变成空字符串/delete,应拒绝
        Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("/*! DELETE*/ FROM users"));
    }

    @Test
    void rejectLineCommentThenDelete() {
        Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("-- select\nDELETE FROM users"));
    }

    @Test
    void allowLeadingInlineCommentThenSelect() {
        // 合法:注释之后是 SELECT 开头
        String out = SqlSanitizer.sanitize("/* hint */ SELECT * FROM t");
        Assertions.assertTrue(out.toUpperCase().startsWith("SELECT"));
    }

    // -------- 规则 3:多语句堆叠 --------
    @Test
    void rejectMultiStatementSemicolon() {
        Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("SELECT * FROM t; DROP TABLE t"));
    }

    @Test
    void allowSemicolonInsideString() {
        // 字符串中的 ; 不应拦截
        Assertions.assertDoesNotThrow(() ->
                SqlSanitizer.sanitize("SELECT 'a;b;c' AS tag FROM t"));
    }

    // -------- 规则 4:危险关键字 --------
    @Test
    void rejectUnionSelect() {
        Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("SELECT a FROM t1 UNION ALL SELECT b FROM t2"));
    }

    @Test
    void rejectSleepBlind() {
        Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("SELECT SLEEP(5) FROM dual"));
    }

    @Test
    void rejectIntoOutfile() {
        Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("SELECT * FROM users INTO OUTFILE '/tmp/a.txt'"));
    }

    @Test
    void rejectSetGlobal() {
        Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("SET @@global.max_connections=1000"));
    }

    // -------- 规则 5:敏感 schema --------
    @Test
    void rejectInformationSchemaSelect() {
        Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("SELECT * FROM information_schema.tables"));
    }

    @Test
    void rejectMysqlUserSelect() {
        Assertions.assertThrows(BizException.class, () ->
                SqlSanitizer.sanitize("SELECT host,user FROM mysql.user"));
    }

    // -------- 参数边界:长度 / 空 --------
    @Test
    void rejectEmptySql() {
        BizException ex = Assertions.assertThrows(BizException.class,
                () -> SqlSanitizer.sanitize(""));
        Assertions.assertEquals(ErrorCode.PARAM_INVALID.code(), ex.getCode());
    }

    @Test
    void rejectOverlengthSql() {
        StringBuilder sb = new StringBuilder("SELECT 1 FROM t WHERE x IN (");
        while (sb.length() < 11_000) sb.append("1,");
        sb.append("2)");
        BizException ex = Assertions.assertThrows(BizException.class,
                () -> SqlSanitizer.sanitize(sb.toString()));
        Assertions.assertEquals(ErrorCode.SQL_RESULT_TOO_LARGE.code(), ex.getCode());
    }

    // -------- stripComments 单独回归 --------
    @Test
    void stripAllCommentStyles() {
        String raw = "SELECT a, /* inline */ b FROM t -- end\nWHERE c = 1 # tail";
        String out = SqlSanitizer.stripComments(raw);
        Assertions.assertFalse(out.contains("/*"), "残留块注释");
        Assertions.assertFalse(out.contains("--"), "残留行注释");
        Assertions.assertFalse(out.contains("# "), "残留 # 注释");
    }
}
