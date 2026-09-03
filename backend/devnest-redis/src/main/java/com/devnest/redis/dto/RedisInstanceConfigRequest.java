package com.devnest.redis.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Redis 实例配置请求体(创建/编辑).
 * 密码为可选明文(null/空 = 无密码或编辑时不更新密码).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 11:00
 */
public record RedisInstanceConfigRequest(

        @NotBlank(message = "实例名称不能为空")
        @Size(max = 64, message = "名称最多 64 字符")
        String name,

        @NotBlank(message = "host 不能为空")
        @Size(max = 128, message = "host 最多 128 字符")
        String host,

        @Min(1) @Max(65535)
        Integer port,

        /** 明文密码(null=无密码;编辑时传 null 表示不修改) */
        String password,

        @Min(0)
        Integer dbIndex,

        @Min(100) @Max(30000)
        Integer timeoutMs,

        @Min(1) @Max(100)
        Integer maxConnections,

        Long sshBastionId,

        @Size(max = 255)
        String remark
) {
    public Integer effectivePort() { return port != null ? port : 6379; }
    public Integer effectiveDbIndex() { return dbIndex != null ? dbIndex : 0; }
    public Integer effectiveTimeoutMs() { return timeoutMs != null ? timeoutMs : 2000; }
    public Integer effectiveMaxConnections() { return maxConnections != null ? maxConnections : 8; }
}
