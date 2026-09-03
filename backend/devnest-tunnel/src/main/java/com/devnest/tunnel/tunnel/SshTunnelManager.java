package com.devnest.tunnel.tunnel;

import com.devnest.common.crypto.CryptoService;
import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.tunnel.config.TunnelProperties;
import com.devnest.tunnel.entity.SshBastion;
import com.devnest.tunnel.entity.SshPortMapping;
import com.devnest.tunnel.model.TunnelState;
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
        inst.setLifecycleListener(this::onInstanceStateChanged);
        inst.start();
        instances.put(bastionId, inst);
        return inst;
    }

    /**
     * 根据隧道实例的状态回调,写入 ssh_port_mapping.last_running 持久字段.
     * RUNNING=已启动(下次重启恢复);ERROR/CLOSED=未启动(下次重启不恢复).
     * 注意:写库事务直接挂在 Repository 方法上(见 SshPortMappingRepository.updateLastRunningByBastionId),
     * 因此此处不需要也不应该加 @Transactional(避免 this 自调用使 AOP 代理失效).
     */
    public void onInstanceStateChanged(Long bastionId, TunnelState newState, String detail) {
        if (bastionId == null) return;
        try {
            if (newState == TunnelState.RUNNING) {
                int rows = mappingRepo.updateLastRunningByBastionId(bastionId, Boolean.TRUE);
                log.info("bastion={} lastRunning 更新为 true, {} 条记录受影响 ({})", bastionId, rows, detail);
            } else if (newState == TunnelState.ERROR || newState == TunnelState.CLOSED) {
                int rows = mappingRepo.updateLastRunningByBastionId(bastionId, Boolean.FALSE);
                log.info("bastion={} lastRunning 更新为 false, {} 条记录受影响 ({})", bastionId, rows, detail);
            }
        } catch (Exception e) {
            log.warn("更新 lastRunning 失败(bastion={}, state={}): {}", bastionId, newState, e.getMessage());
        }
    }

    /**
     * 停止指定跳板的隧道.用户主动调用 → 持久化 lastRunning=false.
     */
    public void stopTunnel(Long bastionId) {
        SshTunnelInstance inst = instances.remove(bastionId);
        if (inst == null) {
            throw new BizException(ErrorCode.TUNNEL_NOT_RUNNING);
        }
        inst.stop(true);
    }

    public SshTunnelInstance getInstance(Long bastionId) {
        return instances.get(bastionId);
    }

    public Collection<SshTunnelInstance> getAllInstances() {
        return instances.values();
    }

    /**
     * 程序退出时统一销毁所有隧道(JVM 钩子触发).
     * 不更新 lastRunning:保持用户上次点击启动的状态,下次服务启动会通过 TunnelBootstrapRunner 继承恢复.
     */
    @PreDestroy
    public void stopAll() {
        log.info("退出前销毁所有 SSH 隧道,共 {} 个(lastRunning 不改动,用于启动继承)", instances.size());
        instances.values().forEach(inst -> inst.stop(false));
        instances.clear();
    }
}
