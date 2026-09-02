package com.devnest.console.ws;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器(安全版).
 * <p>
 * 1. 解析路径 /ws/console/{consoleId} 提取 consoleId,校验数字合法
 * 2. 从 query string 读取 token,调用 WsTokenManager.verifyAndConsume 做 TOFU 校验
 * 3. 校验失败时设置响应 401,避免 WebSocket 框架默认 500 泄露
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
public class ConsoleHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx == path.length() - 1) {
            reject(response, 400, "BAD_PATH");
            return false;
        }
        long consoleId;
        try {
            consoleId = Long.parseLong(path.substring(idx + 1));
        } catch (NumberFormatException e) {
            reject(response, 400, "BAD_CONSOLE_ID");
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
        } catch (BizException e) {
            reject(response, 401, e.getMessage());
            return false;
        }

        attributes.put("consoleId", consoleId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
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

    // 工具:ServerHttpRequest 中读取 HttpServletRequest(保留以便后续扩展)
    @SuppressWarnings("unused")
    private static HttpServletRequest servletRequest(ServerHttpRequest request) {
        return ((ServletServerHttpRequest) request).getServletRequest();
    }
}
