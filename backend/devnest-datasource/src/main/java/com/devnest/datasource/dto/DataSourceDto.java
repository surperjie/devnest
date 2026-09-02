package com.devnest.datasource.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据源返回 DTO(不含密码明文).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Data
@NoArgsConstructor
public class DataSourceDto {
    private Long id;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private Long tunnelBastionId;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
