package com.devnest.tunnel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SSH 隧道运行参数(对应 application.yml 中 devnest.tunnel).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@Component
@ConfigurationProperties(prefix = "devnest.tunnel")
public class TunnelProperties {

    /** SSH 会话建立最大重试次数 */
    private int maxRetries = 5;

    /** 重试间隔(毫秒) */
    private long retryIntervalMs = 3000;

    /** 心跳检测间隔(秒) */
    private long heartbeatIntervalS = 10;

    /** 心跳检测超时(毫秒) */
    private int heartbeatTimeoutMs = 3000;

    /** SSH 连接超时(毫秒) */
    private int connectTimeoutMs = 5000;

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public long getRetryIntervalMs() { return retryIntervalMs; }
    public void setRetryIntervalMs(long retryIntervalMs) { this.retryIntervalMs = retryIntervalMs; }

    public long getHeartbeatIntervalS() { return heartbeatIntervalS; }
    public void setHeartbeatIntervalS(long heartbeatIntervalS) { this.heartbeatIntervalS = heartbeatIntervalS; }

    public int getHeartbeatTimeoutMs() { return heartbeatTimeoutMs; }
    public void setHeartbeatTimeoutMs(int heartbeatTimeoutMs) { this.heartbeatTimeoutMs = heartbeatTimeoutMs; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
}
