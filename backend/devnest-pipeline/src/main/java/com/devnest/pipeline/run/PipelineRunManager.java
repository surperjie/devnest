package com.devnest.pipeline.run;

import com.devnest.pipeline.config.PipelineProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * 流水线运行管理器.
 * <p>
 * 并发模型:
 * - 同一条流水线:同时只允许 1 个活动运行(服务层 DB 行锁为准,此处做二次内存拦截)
 * - 不同流水线:可并行,但全局并发数受信号量(devnest.pipeline.max-concurrent-runs)约束
 * <p>
 * 职责:提交 run 到全局虚拟线程池、持有 RunningHandle 以便停止、优雅关闭时终止所有进程.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Component
public class PipelineRunManager {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunManager.class);

    private final ExecutorService executorService;
    private final PipelineExecutionEngine engine;
    private final PipelineProperties properties;

    private final ConcurrentHashMap<Long, RunningHandle> handlesByRun = new ConcurrentHashMap<>();
    private final Set<Long> runningPipelineIds = ConcurrentHashMap.newKeySet();
    private final Semaphore permits;

    public PipelineRunManager(@Qualifier("virtualExecutorService") ExecutorService executorService,
                              PipelineExecutionEngine engine,
                              PipelineProperties properties) {
        this.executorService = executorService;
        this.engine = engine;
        this.properties = properties;
        this.permits = new Semaphore(Math.max(1, properties.getMaxConcurrentRuns()));
    }

    /** 同流水线是否已在运行/排队中(仅内存快速判断,最终以 DB 为准) */
    public boolean isPipelineRunning(Long pipelineId) {
        return runningPipelineIds.contains(pipelineId);
    }

    /**
     * 提交一个已入库(WAITING)的 run 去执行.
     *
     * @return false 表示同流水线已被占用(理论上被 DB 锁挡在前面,兜底)
     */
    public boolean submit(long runId, long pipelineId) {
        RunningHandle handle = new RunningHandle(runId, pipelineId);
        RunningHandle existed = handlesByRun.putIfAbsent(runId, handle);
        if (existed != null) {
            return true;
        }
        if (!runningPipelineIds.add(pipelineId)) {
            handlesByRun.remove(runId);
            log.warn("[pipeline] 流水线 {} 已有活动运行,run {} 提交被拒", pipelineId, runId);
            return false;
        }
        log.info("[pipeline] run {} 提交执行(流水线 {})", runId, pipelineId);
        executorService.execute(() -> {
            boolean acquired = false;
            try {
                permits.acquire();
                acquired = true;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                handle.cancel();
            }
            try {
                if (acquired) {
                    engine.execute(runId, handle);
                }
            } catch (Exception e) {
                log.error("[pipeline] run {} 引擎执行异常", runId, e);
            } finally {
                if (acquired) {
                    permits.release();
                }
                handlesByRun.remove(runId);
                runningPipelineIds.remove(pipelineId);
                log.info("[pipeline] run {} 释放运行资源", runId);
            }
        });
        return true;
    }

    /**
     * 停止一个 run.
     *
     * @return true=已找到运行句柄并发送停止;false=句柄不存在(可能刚结束)
     */
    public boolean stop(long runId) {
        RunningHandle handle = handlesByRun.get(runId);
        if (handle == null) {
            return false;
        }
        handle.cancel();
        log.info("[pipeline] run {} 收到停止请求", runId);
        return true;
    }

    @PreDestroy
    public void shutdown() {
        log.info("[pipeline] 应用关闭,终止 {} 个运行中的流水线", handlesByRun.size());
        handlesByRun.values().forEach(RunningHandle::cancel);
    }
}
