package com.devnest.console.ws;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * WebSocket 握手拦截器(安全版).
 * <p>
 * 1. 解析路径 /ws/console/{consoleId} 提取 consoleId,校验数字合法
 * 2. 从 query string 读取 token,调用 WsTokenManager.verifyAndConsume 做 TOFU 校验
 * 3. 校验失败时设置响应 401/400,并统一打 WARN 日志(用户反馈"后端没日志"时可快速定位)
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
public class ConsoleHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ConsoleHandshakeInterceptor.class);

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        String client = clientIp(request);
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx == path.length() - 1) {
            reject(response, 400, "BAD_PATH");
            log.warn("WS握手拒绝(路径非法): path={}, client={}", path, client);
            return false;
        }
        long consoleId;
        try {
            consoleId = Long.parseLong(path.substring(idx + 1));
        } catch (NumberFormatException e) {
            reject(response, 400, "BAD_CONSOLE_ID");
            log.warn("WS握手拒绝(consoleId非数字): path={}, client={}", path, client);
            return false;
        }

        // 从 query 读 token: URI query 安全解析
        String token = null;
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("token=")) {
                    token = part.substring("token=".length());
                    break;
                }
            }
        }

        try {
            WsTokenManager.verifyAndConsume(token, consoleId);
            log.info("WS握手通过: consoleId={}, client={}", consoleId, client);
        } catch (BizException e) {
            reject(response, 401, e.getMessage());
            log.warn("WS握手拒绝(token校验失败): consoleId={}, token={}, client={}, reason={}",
                    consoleId, maskToken(token), client, e.getMessage());
            return false;
        }

        attributes.put("consoleId", consoleId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.warn("WS握手后异常: path={}, client={}, err={}",
                    request.getURI().getPath(), clientIp(request), exception.getMessage());
        }
    }

    /** 对 token 做极简脱敏(只保留前后 3 位),避免日志泄露. */
    private static String maskToken(String token) {
        if (token == null) return "<null>";
        int n = token.length();
        if (n <= 8) return "***";
        return token.substring(0, 3) + "***" + token.substring(n - 3);
    }

    /** 读取客户端 IP:优先 X-Forwarded-For,再读 RemoteAddr. */
    private static String clientIp(ServerHttpRequest request) {
        try {
            if (request instanceof ServletServerHttpRequest sr) {
                HttpServletRequest r = sr.getServletRequest();
                String xff = r.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isEmpty()) {
                    int c = xff.indexOf(',');
                    return (c > 0 ? xff.substring(0, c) : xff).trim();
                }
                return r.getRemoteAddr();
            }
            InetSocketAddress addr = request.getRemoteAddress();
            return addr == null ? "<unknown>" : addr.getHostString();
        } catch (Exception e) {
            return "<unknown>";
        }
    }

    // 底层 Servlet 响应直接写回,避免走 Spring 默认 ws 错误处理的 500
    private void reject(ServerHttpResponse response, int status, String msg) {
        if (response instanceof ServletServerHttpResponse sshr) {
            HttpServletResponse r = sshr.getServletResponse();
            try {
                r.reset();
                r.setStatus(status);
                r.setContentType("application/json;charset=UTF-8");
                r.getWriter().write("{\"code\":" + (status == 401 ? ErrorCode.WS_TOKEN_INVALID.code() : 4000)
                        + ",\"msg\":\"" + msg + "\",\"success\":false}");
                r.flushBuffer();
            } catch (Exception ignored) {
                // ignore I/O 失败,框架会回退到默认关闭连接
            }
        } else {
            // ReactorNetty 等:仅设状态
            response.setStatusCode(org.springframework.http.HttpStatus.valueOf(status));
        }
    }
}
