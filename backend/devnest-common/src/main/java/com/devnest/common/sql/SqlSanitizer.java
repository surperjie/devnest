package com.devnest.common.sql;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 安全校验器(黑名单模式).
 * 用户已知数据库密码,权限由数据库自身控制,应用层仅阻止系统级危险操作.
 *
 * 规则:
 * 1. 长度上限 50000 字符
 * 2. 禁止文件系统操作: INTO OUTFILE / INTO DUMPFILE / LOAD_FILE / LOAD DATA
 * 3. 禁止盲注探测: SLEEP / BENCHMARK / PG_SLEEP / WAITFOR DELAY
 * 4. 禁止 UDF 执行: EXEC / EXECUTE / PREPARE / DEALLOCATE
 * 5. 支持多语句分割(按分号,考虑引号转义)
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
public final class SqlSanitizer {

    /** 系统级危险操作(仅阻止文件系统/盲注/UDF,不阻止DML/DDL) */
    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "INTO OUTFILE", "INTO DUMPFILE", "LOAD_FILE", "LOAD DATA",
            "SLEEP(", "BENCHMARK(", "PG_SLEEP(", "WAITFOR DELAY",
            "EXEC ", "EXECUTE ", "PREPARE ", "DEALLOCATE "
    );

    private static final int MAX_SQL_LEN = 50_000;

    /**
     * 校验单条 SQL 安全性,返回去除注释后的 SQL.
     */
    public static String sanitize(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SQL 为空");
        }
        if (sql.length() > MAX_SQL_LEN) {
            throw new BizException(ErrorCode.SQL_RESULT_TOO_LARGE,
                    "SQL 过长,最大允许 " + MAX_SQL_LEN + " 字符");
        }
        String clean = stripComments(sql.trim());
        if (clean.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "SQL 去注释后为空");
        }
        String upper = clean.toUpperCase();
        for (String kw : BLOCKED_KEYWORDS) {
            if (upper.contains(kw)) {
                throw new BizException(ErrorCode.SQL_BLOCKED_BY_WHITELIST,
                        "命中危险操作: " + kw.trim());
            }
        }
        return clean;
    }

    /**
     * 按分号分割多条 SQL(考虑引号内的分号).
     * 返回去除注释后的有效 SQL 列表.
     */
    public static List<String> splitAndSanitize(String sqlText) {
        if (sqlText == null || sqlText.isBlank()) {
            return List.of();
        }
        List<String> raw = splitBySemicolon(sqlText.trim());
        List<String> result = new ArrayList<>(raw.size());
        for (String s : raw) {
            String clean = stripComments(s.trim());
            if (clean.isEmpty()) continue;
            String upper = clean.toUpperCase();
            for (String kw : BLOCKED_KEYWORDS) {
                if (upper.contains(kw)) {
                    throw new BizException(ErrorCode.SQL_BLOCKED_BY_WHITELIST,
                            "命中危险操作: " + kw.trim());
                }
            }
            result.add(clean);
        }
        return result;
    }

    /**
     * 按引号外的分号分割 SQL.
     */
    static List<String> splitBySemicolon(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false, inTick = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char prev = i == 0 ? 0 : s.charAt(i - 1);
            if (!inDouble && !inTick && c == '\'' && prev != '\\') inSingle = !inSingle;
            else if (!inSingle && !inTick && c == '"' && prev != '\\') inDouble = !inDouble;
            else if (!inSingle && !inDouble && c == '`') inTick = !inTick;
            else if (!inSingle && !inDouble && !inTick && c == ';') {
                String part = cur.toString().trim();
                if (!part.isEmpty()) parts.add(part);
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        String last = cur.toString().trim();
        if (!last.isEmpty()) parts.add(last);
        return parts;
    }

    static String stripComments(String sql) {
        String out = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        out = LINE_COMMENT.matcher(out).replaceAll(" ");
        return out.trim();
    }

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(--|#)[^\\r\\n]*");

    private SqlSanitizer() {}
}
