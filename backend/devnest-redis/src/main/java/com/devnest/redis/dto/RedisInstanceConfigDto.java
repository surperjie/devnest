package com.devnest.redis.dto;

import java.time.LocalDateTime;

/**
 * Redis 实例 DTO(列表/详情展示).密码字段已脱敏.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 11:00
 */
public record RedisInstanceConfigDto(

        Long id,
        String name,
        String host,
        Integer port,
        /** true=已配置密码(null/空=无密码) */
        Boolean hasPassword,
        Integer dbIndex,
        Integer timeoutMs,
        Integer maxConnections,
        Long sshBastionId,
        String remark,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        /** 运行态:是否可连通(probe 结果,详情/测试接口实时返回,列表默认 false) */
        Boolean reachable
) {}
