package com.devnest.datasource.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 多语句 SQL 执行结果.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Data
@NoArgsConstructor
public class MultiSqlResult {
    private List<SqlResultItem> results;
    private long totalCostMs;
}
