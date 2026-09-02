package com.devnest.console.session;

import com.devnest.core.spi.TunnelPortForwarder;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * 远程控制台运行时会话:封装 JSch ChannelShell + 双向流.
 * 虚拟线程读取远端输出,通过回调推送到前端 WebSocket.
 * <p>
 * 隧道端口释放通过 TunnelPortForwarder SPI 完成,不再直接依赖 tunnel 内部实现.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
public class ConsoleSession {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSession.class);

    private final String wsSessionId;
    private final ChannelShell channel;
    private final Session sshSession;
    private final OutputStream toRemote;
    private final InputStream fromRemote;
    /** 隧道模式动态分配的本地端口,null=直连模式无需释放 */
    private final Integer allocatedLocalPort;
    /** SPI:统一的隧道端口转发生命周期 */
    private final TunnelPortForwarder portForwarder;
    private final Consumer<String> onOutput;
    /** 统一虚拟线程执行器 */
    private final ExecutorService virtualExecutor;
    private volatile boolean closed = false;
    private volatile Thread readerThread;

    public ConsoleSession(String wsSessionId, ChannelShell channel, Session sshSession,
                          Integer allocatedLocalPort, TunnelPortForwarder portForwarder,
                          ExecutorService virtualExecutor, Consumer<String> onOutput) throws IOException {
        this.wsSessionId = wsSessionId;
        this.channel = channel;
        this.sshSession = sshSession;
        this.toRemote = channel.getOutputStream();
        this.fromRemote = channel.getInputStream();
        this.allocatedLocalPort = allocatedLocalPort;
        this.portForwarder = portForwarder;
        this.virtualExecutor = virtualExecutor;
        this.onOutput = onOutput;
    }

    /**
     * 启动输出读取虚拟线程:循环读 Channel 输出 → 回调前端.
     * 统一通过全局虚拟线程执行器提交,同时记录当前 Thread 引用便于 close 中断.
     */
    public void start() {
        virtualExecutor.submit(() -> {
            readerThread = Thread.currentThread();
            try {
                readLoop();
            } finally {
                readerThread = null;
            }
        });
    }

    private void readLoop() {
        byte[] buf = new byte[4096];
        try {
            int n;
            while (!closed && (n = fromRemote.read(buf)) != -1) {
                String text = new String(buf, 0, n, StandardCharsets.UTF_8);
                onOutput.accept(text);
            }
        } catch (IOException e) {
            if (!closed) {
                onOutput.accept("\r\n\u001b[31m[连接断开: " + e.getMessage() + "]\u001b[0m\r\n");
            }
        } finally {
            close();
        }
    }

    /** 写入用户输入到远端 shell */
    public void write(String input) {
        if (closed) return;
        try {
            toRemote.write(input.getBytes(StandardCharsets.UTF_8));
            toRemote.flush();
        } catch (IOException e) {
            log.warn("写入控制台失败: {}", e.getMessage());
        }
    }

    /** 调整远端 PTY 行列数,适配前端窗口/全屏切换,避免输出超出界限 */
    public void resize(int cols, int rows) {
        if (closed || channel == null) return;
        try {
            channel.setPtySize(cols, rows, cols * 8, rows * 16);
        } catch (Exception e) {
            log.debug("resize PTY 异常: {}", e.getMessage());
        }
    }

    /**
     * 关闭会话:关 Channel + Session + 移隧道转发 + 释放端口.
     * 释放动作走 TunnelPortForwarder.releaseTunnel 统一处理.
     */
    public synchronized void close() {
        if (closed) return;
        closed = true;
        // 先中断读循环(虚拟线程里的阻塞 read 会抛 InterruptedIOException)
        Thread rt = readerThread;
        if (rt != null) rt.interrupt();
        try {
            if (channel != null && channel.isConnected()) channel.disconnect();
        } catch (Exception e) {
            log.debug("关 channel 异常: {}", e.getMessage());
        }
        try {
            if (sshSession != null && sshSession.isConnected()) sshSession.disconnect();
        } catch (Exception e) {
            log.debug("关 session 异常: {}", e.getMessage());
        }
        if (allocatedLocalPort != null) {
            try {
                portForwarder.releaseTunnel(allocatedLocalPort);
            } catch (Exception e) {
                log.warn("释放隧道端口 {} 失败: {}", allocatedLocalPort, e.getMessage());
            }
        }
        log.info("控制台会话[{}]已关闭", wsSessionId);
    }
}
