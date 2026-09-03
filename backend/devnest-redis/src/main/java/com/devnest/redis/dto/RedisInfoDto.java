package com.devnest.redis.dto;

/**
 * INFO 概览 DTO.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 11:00
 */
public record RedisInfoDto(

        String version,
        String mode,
        /** 已连接客户端数 */
        Long connectedClients,
        /** 已用内存(MB) */
        String memoryUsed,
        /** Redis 启动后累计执行命令 */
        Long totalCommandsProcessed,
        /** 当前 db key 总数 */
        Long dbSize,
        /** INFO 完整字符串(前端可展开看原始) */
        String rawInfo
) {}
