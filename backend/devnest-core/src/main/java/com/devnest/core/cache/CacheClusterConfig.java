package com.devnest.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 统一 Caffeine 缓存集群配置.
 * - 通过 Spring CacheManager 管理命名缓存(默认规格),供业务 @Cacheable 使用.
 * - 同时暴露若干专用 Cache Bean,用于非注解式的精细缓存(如 datasource schema 树、AI 限流、SQL 结果).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
@Configuration
@EnableCaching
public class CacheClusterConfig {

    /**
     * 默认 Spring Cache 规格:小容量 + 中等 TTL,用于跳板状态/结果的通用热数据.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cm = new CaffeineCacheManager();
        cm.setAllowNullValues(true);
        cm.setCaffeine(Caffeine.newBuilder()
                .scheduler(Scheduler.systemScheduler())
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());
        return cm;
    }

    /** 专用:数据源 schema 元数据(库/表/列)缓存,datasource 模块编辑配置后手动失效 */
    @Bean(name = "datasourceSchemaCache")
    public Cache<Long, Object> datasourceSchemaCache() {
        return Caffeine.newBuilder()
                .scheduler(Scheduler.systemScheduler())
                .maximumSize(200)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    /** 专用:AI 调用速率限制(按用户/时间窗口),防止超额度 API Key 被封 */
    @Bean(name = "aiRateLimitCache")
    public Cache<String, Long> aiRateLimitCache() {
        return Caffeine.newBuilder()
                .scheduler(Scheduler.systemScheduler())
                .maximumSize(10_000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}
