package com.devnest.core.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 统一虚拟线程执行器配置.
 * <p>
 * 全项目所有异步 I/O(隧道心跳/控制台读循环/AI 调用/长 SQL)都通过此 Executor 提交,
 * 禁止再散写 Executors.newVirtualThreadPerTaskExecutor() 或 Thread.startVirtualThread(),
 * 便于统一监控和优雅关闭.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 15:55
 */
@Configuration
public class VirtualThreadExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadExecutorConfig.class);

    /**
     * 统一虚拟线程池(ForkJoinPool 风格,按需建虚拟线程,无固定线程数).
     * 建议名称前缀便于 jstack/Arthas 识别.
     */
    @Bean(name = "virtualExecutorService", destroyMethod = "shutdown")
    public ExecutorService virtualExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * DisposableBean:确保 Spring 容器关闭前等待虚拟线程任务最多 3s,避免 OOM 时的资源泄露.
     */
    @Bean
    public DisposableBean virtualExecutorCleanup(ExecutorService virtualExecutorService) {
        return () -> {
            log.info("正在关闭虚拟线程执行器,等待最多 3s...");
            virtualExecutorService.shutdown();
            if (!virtualExecutorService.awaitTermination(3, TimeUnit.SECONDS)) {
                int dropped = virtualExecutorService.shutdownNow().size();
                log.warn("虚拟线程未全部完成,强制关闭,丢弃 {} 个任务", dropped);
            } else {
                log.info("虚拟线程执行器已优雅关闭");
            }
        };
    }
}
