package com.devnest.console.session;

import com.devnest.common.crypto.CryptoService;
import com.devnest.core.spi.TunnelPortForwarder;
import com.devnest.console.entity.RemoteConsole;
import com.devnest.console.repository.RemoteConsoleRepository;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * 远程控制台运行时会话管理器.
 * 按 WebSocket 会话 ID 维护 ChannelShell 实例,支持直连/隧道两种连接模式.
 * <p>
 * 隧道连接统一通过 TunnelPortForwarder SPI,不再硬依赖 SshTunnelManager/PortAllocator.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
@Component
@RequiredArgsConstructor
public class ConsoleSessionManager {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSessionManager.class);

    private final RemoteConsoleRepository consoleRepo;
    private final CryptoService crypto;
    private final TunnelPortForwarder portForwarder;
    private final ExecutorService virtualExecutor;

    private final Map<String, ConsoleSession> sessions = new ConcurrentHashMap<>();

    /**
     * 打开控制台会话.
     *
     * @param wsSessionId WebSocket 会话 ID(前端连接的唯一标识)
     * @param consoleId   控制台配置 ID
     * @param onOutput    远端输出回调(推送到前端 WebSocket)
     */
    public void openSession(String wsSessionId, Long consoleId, Consumer<String> onOutput) {
        if (sessions.containsKey(wsSessionId)) {
            throw new IllegalStateException("控制台会话已存在: " + wsSessionId);
        }
        try {
            RemoteConsole config = consoleRepo.findById(consoleId)
                    .orElseThrow(() -> new IllegalArgumentException("控制台不存在: " + consoleId));
            String password = config.decryptPassword(crypto);

            Integer allocatedPort = null;
            String connectHost;
            int connectPort;

            if (config.getBastionId() != null) {
                // 隧道模式:经跳板隧道连内网主机(SPI 建立转发)
                allocatedPort = portForwarder.allocateTunnel(
                        config.getBastionId(), config.getRemoteHost(), config.getRemotePort());
                connectHost = "127.0.0.1";
                connectPort = allocatedPort;
                log.info("控制台[{}]隧道模式: 127.0.0.1:{} → {}:{}",
                        config.getName(), allocatedPort, config.getRemoteHost(), config.getRemotePort());
            } else {
                // 直连模式
                connectHost = config.getRemoteHost();
                connectPort = config.getRemotePort();
                log.info("控制台[{}]直连模式: {}:{}", config.getName(), connectHost, connectPort);
            }

            JSch jsch = new JSch();
            Session session = jsch.getSession(config.getSshUser(), connectHost, connectPort);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);
            ChannelShell channel = (ChannelShell) session.openChannel("shell");
            channel.setPtyType("xterm");
            channel.setPtySize(80, 24, 800, 480);
            channel.connect(3000);

            ConsoleSession cs = new ConsoleSession(wsSessionId, channel, session,
                    allocatedPort, portForwarder, virtualExecutor, onOutput);
            cs.start();
            sessions.put(wsSessionId, cs);
            log.info("控制台会话[{}]已建立", wsSessionId);
        } catch (Exception e) {
            log.error("打开控制台会话失败: {}", e.getMessage(), e);
            throw new RuntimeException("打开控制台失败: " + e.getMessage(), e);
        }
    }

    public void writeSession(String wsSessionId, String input) {
        ConsoleSession cs = sessions.get(wsSessionId);
        if (cs != null) cs.write(input);
    }

    /** 调整远端 PTY 行列数,适配前端窗口/全屏切换 */
    public void resizeSession(String wsSessionId, int cols, int rows) {
        ConsoleSession cs = sessions.get(wsSessionId);
        if (cs != null) cs.resize(cols, rows);
    }

    public void closeSession(String wsSessionId) {
        ConsoleSession cs = sessions.remove(wsSessionId);
        if (cs != null) cs.close();
    }

    @PreDestroy
    public void closeAll() {
        log.info("退出前关闭所有控制台会话,共 {} 个", sessions.size());
        sessions.values().forEach(ConsoleSession::close);
        sessions.clear();
    }
}
