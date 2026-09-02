package com.devnest.datasource.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 单条 SQL 执行结果.
 * SELECT 返回 columns+rows; INSERT/UPDATE/DELETE 返回 affectedRows.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Data
@NoArgsConstructor
public class SqlResultItem {
    /** 原始 SQL */
    private String sql;
    /** 列名(SELECT 时有值) */
    private List<String> columns;
    /** 数据行(SELECT 时有值) */
    private List<Map<String, Object>> rows;
    /** 影响行数(DML 时有值) */
    private int affectedRows;
    /** 耗时(ms) */
    private long costMs;
    /** SUCCESS / FAILED */
    private String status;
    /** 失败时的错误信息 */
    private String errorMsg;
}
