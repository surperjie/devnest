package com.devnest.core.pool;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 动态业务数据源连接池参数(对应 application.yml 中 devnest.pool).
 * <p>
 * 这些参数直接决定"最多会向目标库开多少连接",是过载保护的第一道闸门:
 * 理论最大连接数 ≈ max-pools × max-pool-size(默认 20 × 8 = 160),
 * 请确保目标库的 max_connections 留有余量(MySQL 8 默认 151,必要时下调这两项).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/10 11:30
 */
@Component
@ConfigurationProperties(prefix = "devnest.pool")
public class PoolProperties {

    /** 单个数据源连接池的最大连接数 */
    private int maxPoolSize = 8;

    /** 单个池的最小空闲连接数;设 0 则空闲时连接全部归还,代价是首次查询需重建连接 */
    private int minIdle = 1;

    /** 池内空闲连接存活时长(毫秒),超过即关闭归还给目标库 */
    private long idleTimeoutMs = 600_000;

    /** 获取连接的最长等待(毫秒),超时快速失败而不是无限阻塞 */
    private long connectionTimeoutMs = 2000;

    /** 连接泄漏检测阈值(毫秒):连接被借出超过该时长未归还即告警;<=0 表示禁用(须为 0 或 >=2000) */
    private long leakDetectionMs = 2000;

    /** 同时存在的业务连接池数量上限,防止池数量无界增长耗尽目标库连接与本机文件句柄 */
    private int maxPools = 20;

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public void setIdleTimeoutMs(long idleTimeoutMs) {
        this.idleTimeoutMs = idleTimeoutMs;
    }

    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public long getLeakDetectionMs() {
        return leakDetectionMs;
    }

    public void setLeakDetectionMs(long leakDetectionMs) {
        this.leakDetectionMs = leakDetectionMs;
    }

    public int getMaxPools() {
        return maxPools;
    }

    public void setMaxPools(int maxPools) {
        this.maxPools = maxPools;
    }
}
