package com.devnest.core.pool;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动态业务数据源连接池管理器.
 * <p>
 * 三期 datasource 模块使用,与本地配置库(H2/MySQL)的连接池完全隔离.
 * 提供:
 * - 按 datasourceId 并发安全懒创建
 * - 统一 Hikari 参数(全部来自 {@link PoolProperties},不硬编码)
 * - 池数量总量闸门(max-pools),防止池无界增长打爆目标库与本机文件句柄
 * - Micrometer 指标导出(Actuator/metrics 可见)
 * - 容器销毁时关闭所有业务池
 * <p>
 * 关于配置变更:本类只负责"建"与"销毁",不负责失效时机.
 * 配置变更后由上层({@code DatabaseQueryService.evict})调用 {@link #destroyPool} 丢弃旧池,
 * 下一次查询经 {@link #getOrCreate} 按新配置重建;如需给进行中的查询留收尾时间,
 * 由调用方自行延迟调用 destroyPool(隧道端口与池必须在同一时机回收,故不在此处做延迟).
 * <p>
 * 资源上限:理论最大连接数 ≈ max-pools × max-pool-size(默认 20 × 8 = 160),
 * 实际同时活跃的池远少于上限;空闲池内连接会按 idle-timeout-ms 归还给目标库.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
@Component
public class HikariPoolFactory {

    private static final Logger log = LoggerFactory.getLogger(HikariPoolFactory.class);

    /** Hikari 对泄漏检测阈值的硬性下限,低于该值会被拒绝 */
    private static final long MIN_LEAK_DETECTION_MS = 2000L;

    private final PoolProperties props;

    private final Map<String, HikariDataSource> poolMap = new ConcurrentHashMap<>();

    /** 已创建池的计数,与 poolMap 同步维护,用于上限判断(避免 size() 在并发下的瞬时不准) */
    private final AtomicInteger poolCount = new AtomicInteger();

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    public HikariPoolFactory(PoolProperties props) {
        this.props = props;
    }

    /** 把"当前活跃池数量"暴露为指标,便于观察是否逼近 max-pools 上限 */
    @jakarta.annotation.PostConstruct
    void registerPoolCountMetric() {
        if (meterRegistry == null) {
            return;
        }
        Gauge.builder("devnest.pool.active.count", poolCount, AtomicInteger::get)
                .description("当前活跃的业务数据源连接池数量")
                .register(meterRegistry);
    }

    /**
     * 获取或创建业务连接池.
     *
     * @param poolKey 连接池唯一键,一般为 "ds:" + datasourceId
     * @param jdbcUrl JDBC URL
     * @param driver  驱动类全限定名
     * @param user    用户名
     * @param pass    密码(调用方已解密)
     * @return 已建立的 HikariDataSource
     * @throws BizException DATASOURCE_POOL_LIMIT_EXCEEDED 池数量已达上限
     */
    public HikariDataSource getOrCreate(String poolKey, String jdbcUrl, String driver,
                                        String user, String pass) {
        HikariDataSource existing = poolMap.get(poolKey);
        if (existing != null) {
            return existing;
        }
        // 总量闸门:先判断再建池,避免"建好才发现超限"造成的瞬时连接浪费
        int limit = Math.max(1, props.getMaxPools());
        if (poolCount.get() >= limit) {
            throw new BizException(ErrorCode.DATASOURCE_POOL_LIMIT_EXCEEDED,
                    "已达连接池上限 " + limit + ",请先删除不再使用的数据源");
        }
        return poolMap.computeIfAbsent(poolKey, k -> {
            HikariDataSource ds = buildPool(k, jdbcUrl, driver, user, pass);
            poolCount.incrementAndGet();
            return ds;
        });
    }

    /**
     * 销毁指定连接池(数据源删除、配置变更时调用).
     * 关闭后同一 poolKey 再次 {@link #getOrCreate} 会按传入的新配置重建.
     * 调用方若希望进行中的查询跑完,应自行延迟调用本方法.
     */
    public void destroyPool(String poolKey) {
        HikariDataSource pool = poolMap.remove(poolKey);
        if (pool != null) {
            poolCount.decrementAndGet();
            safeClose(pool);
        }
    }

    /** 当前活跃连接池数量(诊断用) */
    public int activePoolCount() {
        return poolCount.get();
    }

    /** 容器销毁:关闭所有动态业务连接池 */
    public void destroyAll() {
        log.info("销毁动态 Hikari 业务连接池,共 {} 个", poolMap.size());
        poolMap.values().forEach(this::safeClose);
        poolMap.clear();
        poolCount.set(0);
    }

    /** 探测连接是否可用,用于前端"测试连接"按钮(临时池,不放入 poolMap,不占 max-pools 名额) */
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
        HikariConfig cfg = baseConfig(jdbcUrl, driver, user, pass, props.getConnectionTimeoutMs());
        cfg.setPoolName("hikari-" + name);
        cfg.setMaximumPoolSize(Math.max(1, props.getMaxPoolSize()));
        cfg.setMinimumIdle(Math.max(0, props.getMinIdle()));
        cfg.setIdleTimeout(props.getIdleTimeoutMs());
        applyLeakDetection(cfg, name);
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

    /**
     * 泄漏检测阈值:0/负数表示禁用;0 到 2000 之间 Hikari 会直接拒绝,这里按 2000 兜底并告警,
     * 避免一个笔误让整个数据源不可用.
     */
    private void applyLeakDetection(HikariConfig cfg, String name) {
        long threshold = props.getLeakDetectionMs();
        if (threshold <= 0) {
            return;
        }
        if (threshold < MIN_LEAK_DETECTION_MS) {
            log.warn("连接池 {} 的 leak-detection-ms={} 低于 Hikari 下限,按 {} 处理",
                    name, threshold, MIN_LEAK_DETECTION_MS);
            threshold = MIN_LEAK_DETECTION_MS;
        }
        cfg.setLeakDetectionThreshold(threshold);
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
        cfg.addDataSourceProperty("characterEncoding", "UTF-8");
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
