package com.devnest.console.ws;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 握手一次性 Token(TOFU: Trust-On-First-Use).
 * <p>
 * 流程:
 * 1. 前端点击"打开控制台" → 调用 POST /api/console/{id}/ws-token(由 HTTP 鉴权体系保护,当前 CORS+127.0.0.1 兜底)
 * 2. 后端生成 32 字节的 Base64 Token,绑定 consoleId + 过期时间(TTL=30s),存入 Map
 * 3. 前端用 ws://127.0.0.1:8080/ws/console/{consoleId}?token=xxx 发起连接
 * 4. HandshakeInterceptor 验证 token 存在、匹配 consoleId、未过期,验证后立即删除(一次性)
 * <p>
 * 防护能力:
 * - 防止未经任何 HTTP 校验的任意 Origin/脚本直接建立 WebSocket(历史 bug:任何人只要拿到 consoleId 就能连接)
 * - 即使攻击者看到网络请求里的 token,30s 过期+一次性,也难以伪造
 * <p>
 * 生命周期:由 Spring 管理(单例 bean),token 表与清理调度器都是<b>实例状态</b>,
 * 不再使用静态字段与静态初始化线程 —— 便于测试隔离,清理任务也能随容器优雅关闭.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
@Component
public class WsTokenManager {

    private static final Logger log = LoggerFactory.getLogger(WsTokenManager.class);

    private static final long TTL_MS = TimeUnit.SECONDS.toMillis(30);

    /** 过期 token 清理周期(秒) */
    private static final long CLEAN_INTERVAL_SECONDS = 10;

    /** key=token  value=long[2] : [consoleId, expireAt] */
    private final Map<String, long[]> tokens = new ConcurrentHashMap<>();

    private final SecureRandom rng = new SecureRandom();

    /** 清理调度器:随 bean 生命周期创建与关闭,避免静态线程脱离容器管理 */
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-token-cleaner");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    void startCleaner() {
        cleaner.scheduleWithFixedDelay(this::cleanExpired,
                CLEAN_INTERVAL_SECONDS, CLEAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopCleaner() {
        cleaner.shutdownNow();
    }

    /** 生成一次性 token,返回 token 字符串 */
    public String issue(long consoleId) {
        byte[] raw = new byte[32];
        rng.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long[] val = {consoleId, System.currentTimeMillis() + TTL_MS};
        tokens.put(token, val);
        return token;
    }

    /**
     * 校验并消耗 token.
     *
     * @return 绑定的 consoleId
     * @throws BizException WS_TOKEN_INVALID 校验失败
     */
    public long verifyAndConsume(String token, long pathConsoleId) {
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.WS_TOKEN_INVALID, "missing");
        }
        long[] entry = tokens.remove(token); // 一次性:取了就删
        if (entry == null) {
            throw new BizException(ErrorCode.WS_TOKEN_INVALID, "not-found");
        }
        long consoleId = entry[0];
        long expireAt = entry[1];
        if (consoleId != pathConsoleId) {
            throw new BizException(ErrorCode.WS_TOKEN_INVALID, "console-mismatch");
        }
        if (System.currentTimeMillis() > expireAt) {
            throw new BizException(ErrorCode.WS_TOKEN_INVALID, "expired");
        }
        return consoleId;
    }

    /** 移除过期条目,防止极端情况下 token 表无限增长. */
    private void cleanExpired() {
        try {
            long now = System.currentTimeMillis();
            int removed = 0;
            Iterator<Map.Entry<String, long[]>> it = tokens.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, long[]> e = it.next();
                if (now > e.getValue()[1]) {
                    it.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                log.debug("清理 {} 个过期 WebSocket token,剩余 {}", removed, tokens.size());
            }
        } catch (Exception e) {
            // 单次清理异常不能中断调度器,否则过期 token 会永久残留
            log.warn("清理过期 WebSocket token 失败: {}", e.getMessage(), e);
        }
    }
}
