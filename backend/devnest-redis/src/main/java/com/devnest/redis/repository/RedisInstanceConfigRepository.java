package com.devnest.redis.repository;

import com.devnest.redis.entity.RedisInstanceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Redis 实例配置 Repository.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 11:00
 */
public interface RedisInstanceConfigRepository extends JpaRepository<RedisInstanceConfig, Long> {

    boolean existsByName(String name);
}
