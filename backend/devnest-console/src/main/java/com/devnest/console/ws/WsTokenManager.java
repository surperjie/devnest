package com.devnest.console.ws;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
public class WsTokenManager {

    private static final Logger log = LoggerFactory.getLogger(WsTokenManager.class);

    private static final long TTL_MS = TimeUnit.SECONDS.toMillis(30);

    /** key=token  value=long[2] : [consoleId, expireAt] */
    private static final Map<String, long[]> TOKENS = new ConcurrentHashMap<>();

    private static final SecureRandom RNG = new SecureRandom();

    static {
        // 启动懒清理线程,移除过期 token(防止极端情况下 TOKENS 无限增长)
        Thread cleaner = new Thread(WsTokenManager::cleanLoop, "ws-token-cleaner");
        cleaner.setDaemon(true);
        cleaner.start();
    }

    /** 生成一次性 token,返回 token 字符串 */
    public static String issue(long consoleId) {
        byte[] raw = new byte[32];
        RNG.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long[] val = {consoleId, System.currentTimeMillis() + TTL_MS};
        TOKENS.put(token, val);
        return token;
    }

    /**
     * 校验并消耗 token.
     *
     * @return 绑定的 consoleId
     * @throws BizException WS_TOKEN_INVALID 校验失败
     */
    public static long verifyAndConsume(String token, long pathConsoleId) {
        if (token == null || token.isBlank()) {
            throw new BizException(ErrorCode.WS_TOKEN_INVALID, "missing");
        }
        long[] entry = TOKENS.remove(token); // 一次性:取了就删
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

    private static void cleanLoop() {
        // 每 10s 扫一次,移除过期条目
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long now = System.currentTimeMillis();
                int removed = 0;
                Iterator<Map.Entry<String, long[]>> it = TOKENS.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, long[]> e = it.next();
                    if (now > e.getValue()[1]) {
                        it.remove();
                        removed++;
                    }
                }
                if (removed > 0) {
                    log.debug("清理 {} 个过期 WebSocket token,剩余 {}", removed, TOKENS.size());
                }
                Thread.sleep(10_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private WsTokenManager() {}
}
