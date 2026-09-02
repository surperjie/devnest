package com.devnest.tunnel.tunnel;

import com.devnest.common.crypto.CryptoService;
import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.tunnel.config.TunnelProperties;
import com.devnest.tunnel.entity.SshBastion;
import com.devnest.tunnel.entity.SshPortMapping;
import com.devnest.tunnel.model.TunnelState;
import com.devnest.tunnel.port.PortAllocator;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 单跳板 SSH 隧道实例(替代原型静态单例).
 * 一跳板一实例,持有 JSch Session + 心跳线程 + 状态机.
 * 重构自原型 SshTunnelManager:实例化 + 状态机 + 端口分配器 + 虚拟线程异步重连.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public class SshTunnelInstance {

    private static final Logger log = LoggerFactory.getLogger(SshTunnelInstance.class);

    private final SshBastion bastion;
    private final List<SshPortMapping> mappings;
    private final TunnelProperties props;
    private final PortAllocator portAllocator;
    private final CryptoService crypto;

    private final JSch jsch = new JSch();
    private volatile Session session;
    private volatile TunnelState state = TunnelState.IDLE;
    private ScheduledExecutorService heartbeatExecutor;
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile int reconnectCount = 0;

    public SshTunnelInstance(SshBastion bastion,
                             List<SshPortMapping> mappings,
                             TunnelProperties props,
                             PortAllocator portAllocator,
                             CryptoService crypto) {
        this.bastion = bastion;
        this.mappings = mappings;
        this.props = props;
        this.portAllocator = portAllocator;
        this.crypto = crypto;
    }

    /**
     * 启动隧道:分配端口 → 建立 SSH 会话 → 绑定端口转发 → 启动心跳.
     */
    public synchronized void start() {
        if (state == TunnelState.RUNNING || state == TunnelState.CONNECTING) {
            throw new BizException(ErrorCode.TUNNEL_ALREADY_RUNNING);
        }
        state = TunnelState.CONNECTING;
        try {
            allocateLocalPorts();
            establishSession();
            state = TunnelState.RUNNING;
            reconnectCount = 0;
            startHeartbeat();
            log.info("隧道[{}]启动成功,共 {} 条映射", bastion.getName(), mappings.size());
        } catch (Exception e) {
            state = TunnelState.ERROR;
            rollbackPorts();
            throw new BizException(ErrorCode.TUNNEL_START_FAILED, e.getMessage());
        }
    }

    /**
     * 停止隧道:关心跳 → 断会话 → 释放端口.
     */
    public synchronized void stop() {
        stopHeartbeat();
        virtualExecutor.shutdownNow();
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        rollbackPorts();
        state = TunnelState.CLOSED;
        log.info("隧道[{}]已关闭", bastion.getName());
    }

    /**
     * 心跳检测失败时,在虚拟线程里异步重连,不阻塞心跳调度.
     */
    private void reconnect() {
        while (state == TunnelState.RECONNECTING && reconnectCount < props.getMaxRetries()) {
            try {
                establishSession();
                state = TunnelState.RUNNING;
                reconnectCount = 0;
                log.info("隧道[{}]重连成功", bastion.getName());
                return;
            } catch (Exception e) {
                reconnectCount++;
                log.warn("隧道[{}]第{}次重连失败:{}", bastion.getName(), reconnectCount, e.getMessage());
                if (reconnectCount >= props.getMaxRetries()) {
                    state = TunnelState.ERROR;
                    log.error("隧道[{}]重连失败,已达最大次数,状态置 ERROR", bastion.getName());
                    return;
                }
                try {
                    Thread.sleep(props.getRetryIntervalMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void allocateLocalPorts() {
        for (SshPortMapping m : mappings) {
            int local = (m.getPreferredLocalPort() != null)
                    ? portAllocator.allocatePreferred(m.getPreferredLocalPort())
                    : portAllocator.allocateAny();
            m.setAllocatedLocalPort(local);
        }
    }

    private void rollbackPorts() {
        for (SshPortMapping m : mappings) {
            if (m.getAllocatedLocalPort() != null) {
                portAllocator.release(m.getAllocatedLocalPort());
                m.setAllocatedLocalPort(null);
            }
        }
    }

    private void establishSession() throws Exception {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        String password = bastion.decryptPassword(crypto);
        int retries = 0;
        while (retries < props.getMaxRetries()) {
            try {
                session = jsch.getSession(bastion.getSshUser(), bastion.getSshHost(), bastion.getSshPort());
                session.setPassword(password);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setTimeout(props.getConnectTimeoutMs());
                session.connect();
                for (SshPortMapping m : mappings) {
                    session.setPortForwardingL(m.getAllocatedLocalPort(), m.getRemoteHost(), m.getRemotePort());
                    log.info("  127.0.0.1:{} → {}:{} [{}]",
                            m.getAllocatedLocalPort(), m.getRemoteHost(), m.getRemotePort(),
                            m.getLabel() == null ? "-" : m.getLabel());
                }
                return;
            } catch (JSchException e) {
                retries++;
                if (retries >= props.getMaxRetries()) {
                    throw new JSchException("SSH 会话建立失败(重试 " + retries + " 次): " + e.getMessage(), e);
                }
                log.warn("  第{}次连接失败:{}, {}ms 后重试", retries, e.getMessage(), props.getRetryIntervalMs());
                Thread.sleep(props.getRetryIntervalMs());
            }
        }
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("ssh-heartbeat-" + bastion.getId());
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(this::heartbeat,
                props.getHeartbeatIntervalS(), props.getHeartbeatIntervalS(), TimeUnit.SECONDS);
    }

    private void heartbeat() {
        if (state != TunnelState.RUNNING) {
            return;
        }
        try {
            boolean allAlive = true;
            for (SshPortMapping m : mappings) {
                if (!checkPortAlive(m.getAllocatedLocalPort())) {
                    allAlive = false;
                    break;
                }
            }
            if (!allAlive) {
                log.warn("隧道[{}]心跳失败,触发异步重连", bastion.getName());
                state = TunnelState.RECONNECTING;
                reconnectCount = 0;
                virtualExecutor.submit(this::reconnect);
            }
        } catch (Exception e) {
            log.error("心跳检测异常: {}", e.getMessage());
        }
    }

    private boolean checkPortAlive(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), props.getHeartbeatTimeoutMs());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void stopHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
    }

    public TunnelState getState() {
        return state;
    }

    public SshBastion getBastion() {
        return bastion;
    }

    public List<SshPortMapping> getMappings() {
        return mappings;
    }

    /**
     * 动态添加端口转发(二期控制台隧道模式用).
     * 在已建立的隧道 Session 上加一条本地→远端映射.
     */
    public void addDynamicForwarding(int localPort, String remoteHost, int remotePort) throws JSchException {
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("隧道未连接,无法添加转发");
        }
        session.setPortForwardingL(localPort, remoteHost, remotePort);
        log.info("隧道[{}]动态转发: 127.0.0.1:{} → {}:{}", bastion.getName(), localPort, remoteHost, remotePort);
    }

    /**
     * 移除动态端口转发(控制台关闭时调用).
     */
    public void removeDynamicForwarding(int localPort) {
        if (session != null && session.isConnected()) {
            try {
                session.delPortForwardingL(localPort);
                log.info("隧道[{}]移除动态转发: {}", bastion.getName(), localPort);
            } catch (JSchException e) {
                log.warn("移除端口转发失败: {}", e.getMessage());
            }
        }
    }
}
