package com.devnest.tunnel.spi;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.core.spi.TunnelPortForwarder;
import com.devnest.tunnel.model.TunnelState;
import com.devnest.tunnel.port.PortAllocator;
import com.devnest.tunnel.tunnel.SshTunnelInstance;
import com.devnest.tunnel.tunnel.SshTunnelManager;
import org.springframework.stereotype.Component;

/**
 * TunnelPortForwarder 默认实现 - 封装 SshTunnelManager + PortAllocator + SshTunnelInstance.
 * 非 tunnel 模块不再直接依赖这些实现类,符合 DIP(依赖倒置原则).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 15:45
 */
@Component
public class TunnelPortForwarderImpl implements TunnelPortForwarder {

    private final SshTunnelManager tunnelManager;
    private final PortAllocator portAllocator;

    public TunnelPortForwarderImpl(SshTunnelManager tunnelManager, PortAllocator portAllocator) {
        this.tunnelManager = tunnelManager;
        this.portAllocator = portAllocator;
    }

    @Override
    public int allocateTunnel(Long bastionId, String remoteHost, int remotePort) {
        if (!isBastionRunning(bastionId)) {
            throw new BizException(ErrorCode.TUNNEL_NOT_RUNNING,
                    "跳板机未启动,id=" + bastionId);
        }
        SshTunnelInstance tunnel = tunnelManager.getInstance(bastionId);
        int port = portAllocator.allocateAny();
        try {
            tunnel.addDynamicForwarding(port, remoteHost, remotePort);
            return port;
        } catch (Exception e) {
            portAllocator.release(port);
            throw new BizException(ErrorCode.TUNNEL_ALLOCATE_FAILED,
                    "建立动态端口转发失败:" + e.getMessage());
        }
    }

    @Override
    public void releaseTunnel(int allocatedLocalPort) {
        // 遍历所有实例尽力移除动态转发(单个 console 只绑一个 bastion,通常只命中一个)
        for (SshTunnelInstance inst : tunnelManager.getAllInstances()) {
            try {
                inst.removeDynamicForwarding(allocatedLocalPort);
            } catch (Exception ignore) {
                // 不是本实例绑定的端口,跳过
            }
        }
        portAllocator.release(allocatedLocalPort);
    }

    @Override
    public boolean isBastionRunning(Long bastionId) {
        if (bastionId == null) return false;
        SshTunnelInstance inst = tunnelManager.getInstance(bastionId);
        return inst != null && inst.getState() == TunnelState.RUNNING;
    }
}
