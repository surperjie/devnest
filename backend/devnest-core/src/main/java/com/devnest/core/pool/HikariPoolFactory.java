package com.devnest.core.pool;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 动态业务数据源连接池管理器.
 * <p>
 * 三期 datasource 模块使用,与本地配置库(H2/MySQL)的连接池完全隔离.
 * 提供:
 * - 按 datasourceId + Hikari 配置并发安全懒创建
 * - 重建 datasource 配置时优雅下线旧池(newPool 成功→swap→5s 后 oldPool.close)
 * - 统一 Hikari 参数 (maxPoolSize/minIdle/leakDetection/connectionTimeout/initFailFast)
 * - Micrometer 指标导出(Actuator/metrics 可见)
 * - 容器销毁时关闭所有业务池
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
@Component
public class HikariPoolFactory {

    private static final Logger log = LoggerFactory.getLogger(HikariPoolFactory.class);

    private final Map<String, HikariDataSource> poolMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService drainScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "hikari-drain");
                t.setDaemon(true);
                return t;
            });

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    // ============ 内部参数常量(可按需暴露为配置类) ============
    private static final int MAX_POOL_SIZE = 15;
    private static final int MIN_IDLE = 3;
    private static final long CONNECTION_TIMEOUT_MS = 2000;
    private static final long IDLE_TIMEOUT_MS = 600_000;      // 10min
    private static final long LEAK_DETECTION_MS = 2000;
    private static final long DRAIN_DELAY_SECONDS = 5L;

    /**
     * 获取或创建业务连接池.
     *
     * @param poolKey 连接池唯一键,一般为 "ds:" + datasourceId + ":" + version
     * @param jdbcUrl JDBC URL
     * @param driver  驱动类全限定名
     * @param user    用户名
     * @param pass    密码(调用方已解密)
     * @return 已建立的 HikariDataSource
     */
    public HikariDataSource getOrCreate(String poolKey, String jdbcUrl, String driver,
                                        String user, String pass) {
        return poolMap.computeIfAbsent(poolKey, k -> buildPool(k, jdbcUrl, driver, user, pass));
    }

    /**
     * 重建连接池(配置变更时调用).
     * 新池初始化成功后原子替换引用,5s 后再关闭旧池(留出当前进行中 SQL 收尾时间),避免 OOM/too many connections.
     *
     * @return 新池
     */
    public HikariDataSource rebuild(String poolKey, String jdbcUrl, String driver,
                                    String user, String pass) {
        HikariDataSource newPool = buildPool(poolKey + "#next", jdbcUrl, driver, user, pass);
        HikariDataSource oldPool = poolMap.put(poolKey, newPool);
        if (oldPool != null) {
            drainScheduler.schedule(() -> safeClose(oldPool), DRAIN_DELAY_SECONDS, TimeUnit.SECONDS);
        }
        return newPool;
    }

    /** 销毁指定连接池(数据源删除时调用) */
    public void destroyPool(String poolKey) {
        HikariDataSource pool = poolMap.remove(poolKey);
        if (pool != null) safeClose(pool);
    }

    /** 容器销毁:关闭所有动态业务连接池 */
    public void destroyAll() {
        log.info("销毁动态 Hikari 业务连接池,共 {} 个", poolMap.size());
        poolMap.values().forEach(this::safeClose);
        poolMap.clear();
        drainScheduler.shutdownNow();
    }

    /** 探测连接是否可用,用于前端"测试连接"按钮(不放入 poolMap) */
    public boolean probe(String jdbcUrl, String driver, String user, String pass, int timeoutMs) {
        HikariConfig cfg = baseConfig(jdbcUrl, driver, user, pass, timeoutMs);
        cfg.setMaximumPoolSize(1);
        cfg.setMinimumIdle(0);
        cfg.setInitializationFailTimeout(timeoutMs);
        try (HikariDataSource ds = new HikariDataSource(cfg);
             Connection ignored = ds.getConnection()) {
            return true;
        } catch (Exception e) {
            log.debug("数据源连通性探测失败: {}", e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------
    private HikariDataSource buildPool(String name, String jdbcUrl, String driver,
                                       String user, String pass) {
        HikariConfig cfg = baseConfig(jdbcUrl, driver, user, pass, CONNECTION_TIMEOUT_MS);
        cfg.setPoolName("hikari-" + name);
        cfg.setMaximumPoolSize(MAX_POOL_SIZE);
        cfg.setMinimumIdle(MIN_IDLE);
        cfg.setIdleTimeout(IDLE_TIMEOUT_MS);
        cfg.setLeakDetectionThreshold(LEAK_DETECTION_MS);
        cfg.setInitializationFailTimeout(5000);
        if (meterRegistry != null) {
            MetricsTrackerFactory factory = new MicrometerMetricsTrackerFactory(meterRegistry);
            cfg.setMetricsTrackerFactory(factory);
        }
        try {
            return new HikariDataSource(cfg);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.DATASOURCE_CONNECT_FAILED, e.getMessage());
        }
    }

    private HikariConfig baseConfig(String jdbcUrl, String driver, String user, String pass,
                                    long connectTimeoutMs) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setDriverClassName(driver);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setConnectionTimeout(connectTimeoutMs);
        cfg.setValidationTimeout(Math.min(connectTimeoutMs, 1000));
        // UTF-8 & 服务端预处理,适配 MySQL 8/DM 8
        cfg.addDataSourceProperty("useUnicode", "true");
        cfg.addDataSourceProperty("characterEncoding", "utf8mb4");
        cfg.addDataSourceProperty("serverTimezone", "Asia/Shanghai");
        cfg.addDataSourceProperty("useSSL", "false");
        return cfg;
    }

    private void safeClose(HikariDataSource ds) {
        try {
            if (ds != null && !ds.isClosed()) ds.close();
        } catch (Exception e) {
            log.warn("关闭 Hikari 连接池失败: {}", e.getMessage());
        }
    }

    // 暴露 destroyAll 为 Spring Bean 生命周期回调(外部 @PreDestroy 调)
    @jakarta.annotation.PreDestroy
    public void onShutdown() {
        destroyAll();
    }
}
