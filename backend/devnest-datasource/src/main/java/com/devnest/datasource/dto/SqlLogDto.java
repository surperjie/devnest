package com.devnest.datasource.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SQL 执行日志 DTO.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Data
@NoArgsConstructor
public class SqlLogDto {
    private Long id;
    private Long datasourceId;
    private String datasourceName;
    private String sqlText;
    private String status;
    private String errorMsg;
    private Long costMs;
    private Integer rowCount;
    private LocalDateTime createTime;
}
