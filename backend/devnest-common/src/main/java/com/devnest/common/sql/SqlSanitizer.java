package com.devnest.common.sql;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI-SQL 与自定义 SQL 执行入口的白名单校验器.
 * 规则清单(AWVS 级旁路防御):
 * 1. 仅允许 SQL 动词开头: SELECT / SHOW / DESCRIBE / DESC / EXPLAIN
 * 2. 去除所有注释后再次校验开头,防止行内注释绕过
 * 3. 禁止多语句分号堆叠
 * 4. 禁止危险关键字(UPDATE/DELETE/DROP/INSERT/INTO OUTFILE 等)
 * 5. 禁止 INFORMATION_SCHEMA / MYSQL / PERFORMANCE_SCHEMA / SYS 等敏感 schema 直查
 * 6. 禁止 UNION / UNION ALL 拼接
 * 7. 语句长度上限 10000 字符,超长抛异常
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
public final class SqlSanitizer {

    private static final List<String> ALLOWED_PREFIXES =
            Arrays.asList("SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(--|#)[^\\r\\n]*");
    private static final Pattern UNION_SELECT =
            Pattern.compile("\\bUNION\\b.*?\\b(ALL|SELECT)\\b", Pattern.DOTALL);

    private static final Set<String> DANGER_KEYWORDS = new HashSet<>(Arrays.asList(
            "UPDATE", "DELETE", "INSERT", "REPLACE", "DROP", "CREATE", "ALTER", "TRUNCATE",
            "GRANT", "REVOKE", "MERGE", "UPSERT", "CALL",
            "INTO OUTFILE", "INTO DUMPFILE", "LOAD_FILE", "SLEEP", "BENCHMARK", "PG_SLEEP",
            "WAITFOR", "XA ", "START TRANSACTION", "COMMIT", "ROLLBACK", "SET @@", "COPY ",
            "RENAME ", "INTO @", "EXECUTE ", "EXEC ", "PREPARE ", "DEALLOCATE "
    ));

    private static final Set<String> SENSITIVE_SCHEMAS = new HashSet<>(Arrays.asList(
            "INFORMATION_SCHEMA", "MYSQL", "PERFORMANCE_SCHEMA", "SYS"
    ));

    private static final int MAX_SQL_LEN = 10_000;

    public static String sanitize(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SQL 为空");
        }
        if (sql.length() > MAX_SQL_LEN) {
            throw new BizException(ErrorCode.SQL_RESULT_TOO_LARGE,
                    "SQL 过长,最大允许 " + MAX_SQL_LEN + " 字符");
        }
        String trimmed = sql.trim();

        if (hasSemicolonOutOfQuotes(trimmed)) {
            throw new BizException(ErrorCode.SQL_BLOCKED_BY_WHITELIST,
                    "[rule3] 禁止多语句堆叠(;)");
        }

        String clean = stripComments(trimmed);
        String upper = clean.toUpperCase();
        String leading = leadingToken(upper);
        boolean prefixOk = ALLOWED_PREFIXES.stream().anyMatch(leading::startsWith);
        if (!prefixOk) {
            throw new BizException(ErrorCode.SQL_BLOCKED_BY_WHITELIST,
                    "[rule1] 仅允许 SELECT/SHOW/DESCRIBE/EXPLAIN,实际开头: " + safePreview(leading, 16));
        }

        for (String kw : DANGER_KEYWORDS) {
            if (upper.contains(kw)) {
                throw new BizException(ErrorCode.SQL_BLOCKED_BY_WHITELIST,
                        "[rule4] 命中危险关键字: " + kw.trim());
            }
        }
        if (UNION_SELECT.matcher(upper).find()) {
            throw new BizException(ErrorCode.SQL_BLOCKED_BY_WHITELIST, "[rule6] 禁止 UNION 拼接");
        }

        for (String schema : SENSITIVE_SCHEMAS) {
            String reg = "(^|[^A-Z0-9_])" + Pattern.quote(schema) + "\\s*\\.";
            if (Pattern.compile(reg).matcher(upper).find()) {
                throw new BizException(ErrorCode.SQL_BLOCKED_BY_WHITELIST,
                        "[rule5] 禁止直查敏感 schema: " + schema);
            }
        }

        return clean;
    }

    static String stripComments(String sql) {
        String out = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        out = LINE_COMMENT.matcher(out).replaceAll(" ");
        return out.trim();
    }

    private static String leadingToken(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == '(') break;
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static boolean hasSemicolonOutOfQuotes(String s) {
        boolean inSingle = false, inDouble = false, inTick = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char prev = i == 0 ? 0 : s.charAt(i - 1);
            if (!inDouble && !inTick && c == '\'' && prev != '\\') inSingle = !inSingle;
            else if (!inSingle && !inTick && c == '"' && prev != '\\') inDouble = !inDouble;
            else if (!inSingle && !inDouble && c == '`') inTick = !inTick;
            else if (!inSingle && !inDouble && !inTick && c == ';') return true;
        }
        return false;
    }

    private static String safePreview(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private SqlSanitizer() {}
}
