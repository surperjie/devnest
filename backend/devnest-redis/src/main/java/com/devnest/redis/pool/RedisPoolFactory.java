package com.devnest.redis.pool;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.core.spi.TunnelPortForwarder;
import com.devnest.redis.entity.RedisInstanceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 连接池工厂:按实例 ID 管理 JedisPool 生命周期.
 * 支持 SSH 隧道模式(通过 TunnelPortForwarder SPI 动态分配本地端口).
 *
 * 连接池 key = "redis:{instanceId}".
 * 隧道端口映射单独维护,池销毁时同步释放隧道端口.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 14:00
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPoolFactory {

    private final TunnelPortForwarder portForwarder;

    /** key = poolKey("redis:{id}") */
    private final Map<String, JedisPool> pools = new ConcurrentHashMap<>();

    /** key = poolKey, value = allocated local port(0 = 非隧道模式) */
    private final Map<String, Integer> tunnelPorts = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ public

    /**
     * 获取或创建指定实例的 Jedis 连接.
     */
    public Jedis borrow(RedisInstanceConfig config, String passwordPlain) {
        String poolKey = poolKey(config.getId());
        JedisPool pool = pools.computeIfAbsent(poolKey, k -> buildPool(config, passwordPlain));
        try {
            Jedis jedis = pool.getResource();
            jedis.ping();
            return jedis;
        } catch (Exception e) {
            // 连接异常时销毁旧池重建一次
            log.warn("[Redis] 连接探测失败,重建池 key={}: {}", poolKey, e.getMessage());
            destroyPool(poolKey);
            JedisPool rebuilt = pools.computeIfAbsent(poolKey, k -> buildPool(config, passwordPlain));
            try {
                return rebuilt.getResource();
            } catch (Exception ex) {
                throw new BizException(ErrorCode.REDIS_CONNECT_FAILED, ex.getMessage());
            }
        }
    }

    /**
     * 测试即时连接(不入池),用于 testConnection.
     */
    public boolean probe(RedisInstanceConfig config, String passwordPlain) {
        String host = config.getHost();
        int port = config.getPort();

        // 隧道模式先建转发
        Integer localPort = null;
        if (config.getSshBastionId() != null) {
            if (!portForwarder.isBastionRunning(config.getSshBastionId())) {
                throw new BizException(ErrorCode.TUNNEL_NOT_RUNNING, "请先启动绑定的 SSH 隧道");
            }
            localPort = portForwarder.allocateTunnel(config.getSshBastionId(), host, port);
            host = "127.0.0.1";
            port = localPort;
        }

        try (Jedis jedis = new Jedis(host, port, config.getTimeoutMs())) {
            if (passwordPlain != null && !passwordPlain.isBlank()) {
                jedis.auth(passwordPlain);
            }
            jedis.ping();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (localPort != null) {
                portForwarder.releaseTunnel(localPort);
            }
        }
    }

    /**
     * 销毁指定实例的连接池并释放隧道端口.
     */
    public void destroyPool(String poolKey) {
        JedisPool pool = pools.remove(poolKey);
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception e) {
                log.warn("[Redis] 关闭连接池异常 {}: {}", poolKey, e.getMessage());
            }
        }
        Integer localPort = tunnelPorts.remove(poolKey);
        if (localPort != null && localPort > 0) {
            try {
                portForwarder.releaseTunnel(localPort);
            } catch (Exception e) {
                log.warn("[Redis] 释放隧道端口异常 port={}: {}", localPort, e.getMessage());
            }
        }
    }

    /**
     * 配置变更后重建池(先销毁再懒加载).
     */
    public void rebuildPool(Long instanceId) {
        destroyPool(poolKey(instanceId));
    }

    /**
     * 服务关闭时释放全部资源.
     */
    public void shutdown() {
        pools.keySet().forEach(this::destroyPool);
    }

    /** 构建连接池 key */
    public static String poolKey(Long instanceId) {
        return "redis:" + instanceId;
    }

    // ------------------------------------------------------------------ private

    private JedisPool buildPool(RedisInstanceConfig config, String passwordPlain) {
        String host = config.getHost();
        int port = config.getPort();

        // 隧道模式:分配本地端口
        if (config.getSshBastionId() != null) {
            if (!portForwarder.isBastionRunning(config.getSshBastionId())) {
                throw new BizException(ErrorCode.TUNNEL_NOT_RUNNING, "请先启动绑定的 SSH 隧道");
            }
            int localPort = portForwarder.allocateTunnel(
                    config.getSshBastionId(), config.getHost(), config.getPort());
            tunnelPorts.put(poolKey(config.getId()), localPort);
            host = "127.0.0.1";
            port = localPort;
            log.info("[Redis] 隧道模式 bastion={} -> 本地端口 {} -> {}:{}",
                    config.getSshBastionId(), localPort, config.getHost(), config.getPort());
        }

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getMaxConnections());
        poolConfig.setMaxIdle(Math.max(2, config.getMaxConnections() / 2));
        poolConfig.setMinIdle(1);
        poolConfig.setMaxWait(Duration.ofMillis(config.getTimeoutMs()));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());

        if (passwordPlain != null && !passwordPlain.isBlank()) {
            return new JedisPool(poolConfig, host, port, config.getTimeoutMs(), passwordPlain);
        }
        return new JedisPool(poolConfig, host, port, config.getTimeoutMs());
    }
}
