package com.devnest.console.ws;

import com.devnest.console.session.ConsoleSessionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * 控制台终端 WebSocket 处理器.
 * 前端连接后建立 ChannelShell,前端输入转发到 shell,shell 输出推送到前端.
 * 消息协议:普通输入发原始文本;窗口尺寸变更发 JSON {"type":"resize","cols":N,"rows":N}.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
@Component
@RequiredArgsConstructor
public class ConsoleWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ConsoleWebSocketHandler.class);

    private final ConsoleSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long consoleId = (Long) session.getAttributes().get("consoleId");
        if (consoleId == null) {
            sendMessage(session, "\r\n[握手失败: 缺少 consoleId]\r\n");
            closeQuietly(session, CloseStatus.BAD_DATA);
            return;
        }
        try {
            sessionManager.openSession(session.getId(), consoleId, msg -> sendMessage(session, msg));
            log.info("WebSocket 控制台连接已建立: {} (consoleId={})", session.getId(), consoleId);
        } catch (Exception e) {
            log.error("打开控制台会话失败: {}", e.getMessage());
            sendMessage(session, "\r\n\u001b[31m[启动失败: " + e.getMessage() + "]\u001b[0m\r\n");
            closeQuietly(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        // 仅当以 { 开头才尝试解析为 resize 指令,避免和普通输入冲突
        if (payload.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(payload);
                if ("resize".equals(node.path("type").asText())) {
                    int cols = node.path("cols").asInt();
                    int rows = node.path("rows").asInt();
                    if (cols > 0 && rows > 0) {
                        sessionManager.resizeSession(session.getId(), cols, rows);
                        return;
                    }
                }
            } catch (Exception ignored) {
                // 解析失败按普通输入处理(用户可能输入 { 开头内容)
            }
        }
        sessionManager.writeSession(session.getId(), payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.closeSession(session.getId());
        log.info("WebSocket 控制台连接已关闭: {} ({})", session.getId(), status);
    }

    private void sendMessage(WebSocketSession session, String text) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(text));
        } catch (IOException e) {
            log.warn("发送 WebSocket 消息失败: {}", e.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("关闭 WebSocket 异常: {}", e.getMessage());
        }
    }
}
