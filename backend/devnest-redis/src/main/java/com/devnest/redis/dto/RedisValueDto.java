package com.devnest.redis.dto;

import java.util.List;
import java.util.Map;

/**
 * 统一 Redis 值 DTO,按 key 类型填充对应字段.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 11:00
 */
public record RedisValueDto(

        String key,
        /** STRING / LIST / HASH / SET / ZSET / NONE / STREAM */
        String type,
        /** 剩余 TTL 秒数;-1=永久,-2=key 不存在 */
        Long ttl,
        /** 字符串/String 序列化后的 JSON 安全展示 */
        String stringValue,
        /** HASH 键值对 */
        Map<String, String> hashValue,
        /** LIST/SET/ZSET 用 List;ZSET 的 score 后缀在字符串里如 "value (score=1.0)" */
        List<String> listValue,
        String errorMsg
) {
    public static RedisValueDto ok(String key, String type, Long ttl,
                                   String stringValue, Map<String, String> hashValue,
                                   List<String> listValue) {
        return new RedisValueDto(key, type, ttl, stringValue, hashValue, listValue, null);
    }
    public static RedisValueDto none(String key, Long ttl) {
        return new RedisValueDto(key, "NONE", ttl, null, null, null, null);
    }
    public static RedisValueDto error(String key, String msg) {
        return new RedisValueDto(key, "ERROR", null, null, null, null, msg);
    }
}
