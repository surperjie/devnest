package com.devnest.datasource.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 表数据预览/SQL 查询结果.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Data
@NoArgsConstructor
public class TableDataResult {
    /** 列名列表 */
    private List<String> columns;
    /** 数据行(每行 key=列名 value=单元格值) */
    private List<Map<String, Object>> rows;
    /** 总行数(分页时) */
    private long total;
    /** 耗时(ms) */
    private long costMs;
}
