package com.devnest.tunnel.port;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地端口分配器:扫描空闲端口,避免隧道端口冲突.
 * 维护已分配端口集合,支持释放回收.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@Component
public class PortAllocator {

    private static final int MIN_PORT = 10000;
    private static final int MAX_PORT = 65535;

    private final Set<Integer> allocated = ConcurrentHashMap.newKeySet();

    /**
     * 尝试占用指定端口(用户期望端口).成功返回该端口,失败抛异常.
     */
    public int allocatePreferred(int preferred) {
        if (isFree(preferred) && allocated.add(preferred)) {
            return preferred;
        }
        throw new BizException(ErrorCode.PORT_ALLOCATE_FAILED,
                "期望端口 " + preferred + " 被占用");
    }

    /**
     * 自动分配空闲端口(从随机位置扫描).
     */
    public int allocateAny() {
        int start = MIN_PORT + (int) (Math.random() * (MAX_PORT - MIN_PORT));
        for (int i = 0; i < MAX_PORT - MIN_PORT; i++) {
            int port = start + i;
            if (port > MAX_PORT) {
                port = MIN_PORT + (port - MAX_PORT - 1);
            }
            if (isFree(port) && allocated.add(port)) {
                return port;
            }
        }
        throw new BizException(ErrorCode.PORT_ALLOCATE_FAILED, "无可用本地端口");
    }

    /**
     * 释放端口(隧道停止/程序退出时调用).
     */
    public void release(int port) {
        allocated.remove(port);
    }

    private boolean isFree(int port) {
        if (port < 1 || port > 65535) {
            return false;
        }
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
