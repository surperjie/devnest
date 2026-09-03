package com.devnest.redis.dto;

import java.util.List;

/**
 * SCAN 结果返回.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 11:00
 */
public record RedisKeyListDto(

        /** SCAN 下一轮游标(0=遍历结束) */
        String cursor,
        /** 本轮返回的 key */
        List<String> keys,
        /** 当前 db 的 key 总数(DBSIZE,近似值) */
        Long dbSize
) {}
