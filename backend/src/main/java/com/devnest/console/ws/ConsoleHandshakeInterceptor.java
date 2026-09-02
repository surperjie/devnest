package com.devnest.console.ws;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器:从 URL /ws/console/{consoleId} 提取 consoleId 放入会话属性.
 * WebSocket 不支持 Spring MVC 的 @PathVariable,需手动解析路径.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
public class ConsoleHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        // 期望路径 /ws/console/{consoleId}
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx == path.length() - 1) {
            return false;
        }
        try {
            Long consoleId = Long.parseLong(path.substring(idx + 1));
            attributes.put("consoleId", consoleId);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
