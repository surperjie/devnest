package com.devnest.tunnel.tunnel;

import com.devnest.common.crypto.CryptoService;
import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.tunnel.config.TunnelProperties;
import com.devnest.tunnel.entity.SshBastion;
import com.devnest.tunnel.entity.SshPortMapping;
import com.devnest.tunnel.port.PortAllocator;
import com.devnest.tunnel.repository.SshBastionRepository;
import com.devnest.tunnel.repository.SshPortMappingRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * SSH 隧道多实例管理器(Spring Bean).
 * 按 bastionId 维护实例,统一启动/停止/查询/优雅关闭.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@Component
public class SshTunnelManager {

    private static final Logger log = LoggerFactory.getLogger(SshTunnelManager.class);

    private final Map<Long, SshTunnelInstance> instances = new ConcurrentHashMap<>();

    private final SshBastionRepository bastionRepo;
    private final SshPortMappingRepository mappingRepo;
    private final TunnelProperties props;
    private final PortAllocator portAllocator;
    private final CryptoService crypto;
    private final ExecutorService virtualExecutor;

    public SshTunnelManager(SshBastionRepository bastionRepo,
                            SshPortMappingRepository mappingRepo,
                            TunnelProperties props,
                            PortAllocator portAllocator,
                            CryptoService crypto,
                            ExecutorService virtualExecutorService) {
        this.bastionRepo = bastionRepo;
        this.mappingRepo = mappingRepo;
        this.props = props;
        this.portAllocator = portAllocator;
        this.crypto = crypto;
        this.virtualExecutor = virtualExecutorService;
    }

    /**
     * 启动指定跳板的隧道.
     */
    public SshTunnelInstance startTunnel(Long bastionId) {
        if (instances.containsKey(bastionId)) {
            throw new BizException(ErrorCode.TUNNEL_ALREADY_RUNNING);
        }
        SshBastion bastion = bastionRepo.findById(bastionId)
                .orElseThrow(() -> new BizException(ErrorCode.BASTION_NOT_FOUND));
        List<SshPortMapping> mappings = mappingRepo.findByBastionId(bastionId);
        if (mappings.isEmpty()) {
            throw new BizException(ErrorCode.PORT_MAPPING_NOT_FOUND, "跳板下无端口映射");
        }
        SshTunnelInstance inst = new SshTunnelInstance(
                bastion, mappings, props, portAllocator, crypto, virtualExecutor);
        inst.start();
        instances.put(bastionId, inst);
        return inst;
    }

    /**
     * 停止指定跳板的隧道.
     */
    public void stopTunnel(Long bastionId) {
        SshTunnelInstance inst = instances.remove(bastionId);
        if (inst == null) {
            throw new BizException(ErrorCode.TUNNEL_NOT_RUNNING);
        }
        inst.stop();
    }

    public SshTunnelInstance getInstance(Long bastionId) {
        return instances.get(bastionId);
    }

    public Collection<SshTunnelInstance> getAllInstances() {
        return instances.values();
    }

    /**
     * 程序退出时统一销毁所有隧道(JVM 钩子触发).
     */
    @PreDestroy
    public void stopAll() {
        log.info("退出前销毁所有 SSH 隧道,共 {} 个", instances.size());
        instances.values().forEach(SshTunnelInstance::stop);
        instances.clear();
    }
}
