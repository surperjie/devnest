package com.devnest.console.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置:注册控制台终端端点 /ws/console/{consoleId}.
 * 允许前端(127.0.0.1:1420 开发 / tauri 打包)跨域连接.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ConsoleWebSocketHandler consoleWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(consoleWebSocketHandler, "/ws/console/{consoleId}")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new ConsoleHandshakeInterceptor());
    }
}
