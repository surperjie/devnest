package com.devnest.core.spi;

/**
 * SSH 隧道端口转发 SPI:任何需要通过跳板机建立端口转发的模块(console/datasource/redis)都依赖此接口.
 * 业务侧不感知 SshTunnelManager/PortAllocator/SshTunnelInstance 的实现细节.
 *
 * 用法:
 * 1. allocateTunnel 开启一段转发:本地端口 - 跳板机 ID - 目标 host:port
 * 2. 业务侧连 127.0.0.1:allocatedPort 即可走到目标
 * 3. 用完 release(allocatedPort) 释放本地端口和转发规则
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 15:40
 */
public interface TunnelPortForwarder {

    /**
     * 分配动态端口并建立转发.
     *
     * @param bastionId  跳板机 ID(对应 SshBastion)
     * @param remoteHost 隧道目标地址(内网)
     * @param remotePort 隧道目标端口
     * @return 已分配本地端口
     * @throws RuntimeException 跳板机未启动/分配失败时抛业务异常
     */
    int allocateTunnel(Long bastionId, String remoteHost, int remotePort);

    /** 释放指定本地端口并移除隧道转发规则 */
    void releaseTunnel(int allocatedLocalPort);

    /** 查询跳板机是否处于 RUNNING 状态,用于建立转发前校验 */
    boolean isBastionRunning(Long bastionId);
}
