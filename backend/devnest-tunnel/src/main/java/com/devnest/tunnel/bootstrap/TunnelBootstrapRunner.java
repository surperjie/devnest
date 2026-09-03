package com.devnest.tunnel.bootstrap;

import com.devnest.tunnel.repository.SshPortMappingRepository;
import com.devnest.tunnel.service.SshTunnelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务启动时,根据 ssh_port_mapping.last_running=true 自动继承上次启动的隧道.
 * 每个 bastion 只启动一次(Repository 返回去重后的 bastionId).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 17:40
 */
@Component
public class TunnelBootstrapRunner {

    private static final Logger log = LoggerFactory.getLogger(TunnelBootstrapRunner.class);

    private final SshPortMappingRepository mappingRepo;
    private final SshTunnelService tunnelService;
    private final ExecutorService virtualExecutor;

    public TunnelBootstrapRunner(SshPortMappingRepository mappingRepo,
                                 SshTunnelService tunnelService,
                                 ExecutorService virtualExecutor) {
        this.mappingRepo = mappingRepo;
        this.tunnelService = tunnelService;
        this.virtualExecutor = virtualExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restoreRunningTunnels() {
        List<Long> bastionIds;
        try {
            bastionIds = mappingRepo.findDistinctBastionIdsByLastRunningTrue();
        } catch (Exception e) {
            // 数据库尚未初始化/V6 迁移未执行/降级模式等情况,静默跳过
            log.warn("查询 last_running=true 的隧道失败,跳过启动时继承: {}", e.getMessage());
            return;
        }
        if (bastionIds == null || bastionIds.isEmpty()) {
            log.info("无需要继承启动的 SSH 隧道(last_running=true 记录为空)");
            return;
        }
        log.info("检测到 {} 个需要继承启动的跳板,开始并发恢复...", bastionIds.size());
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        for (Long id : bastionIds) {
            virtualExecutor.submit(() -> {
                try {
                    tunnelService.startTunnel(id);
                    success.incrementAndGet();
                    log.info("继承启动跳板 bastionId={} 成功", id);
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.warn("继承启动跳板 bastionId={} 失败(忽略,不阻断服务): {}", id, e.getMessage());
                }
            });
        }
        // 快速等待 2s,让大部分轻量级任务在启动日志完成前输出(非强制,不阻塞 Web 端口启动)
        virtualExecutor.submit(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            log.info("SSH 隧道继承启动阶段结束:成功={},失败={}", success.get(), failed.get());
        });
    }
}
