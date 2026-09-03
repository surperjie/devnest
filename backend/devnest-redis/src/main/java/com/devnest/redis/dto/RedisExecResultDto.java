package com.devnest.redis.dto;

/**
 * 单条 Redis 命令执行结果.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 11:00
 */
public record RedisExecResultDto(

        /** true=成功,false=失败 */
        Boolean success,
        /** 原始命令(回显) */
        String command,
        /** 执行耗时(ms) */
        Long costMs,
        /** 命令输出(字符串/数字/JSON array 字符串化) */
        String output,
        /** 失败原因(success=false 时有值) */
        String errorMsg
) {}
