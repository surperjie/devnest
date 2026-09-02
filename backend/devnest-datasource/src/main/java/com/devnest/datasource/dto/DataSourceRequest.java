package com.devnest.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 数据源创建/更新请求.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Data
public class DataSourceRequest {
    @NotBlank(message = "连接名称不能为空")
    private String name;
    @NotBlank(message = "数据库类型不能为空")
    private String dbType;
    @NotBlank(message = "HOST 不能为空")
    private String host;
    @NotNull(message = "端口不能为空")
    private Integer port;
    /** 库名,留空则可查看整个数据库服务器的所有库 */
    private String databaseName;
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    private Long tunnelBastionId;
    private String remark;
}
