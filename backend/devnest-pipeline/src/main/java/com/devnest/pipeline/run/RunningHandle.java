package com.devnest.pipeline.run;

import com.devnest.pipeline.exec.ProcessKiller;

/**
 * 运行句柄:引擎执行期间被 PipelineRunManager 持有,
 * 用于停止(取消标志 + 结束当前子进程)与并发去重.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public class RunningHandle {

    private final long runId;
    private final long pipelineId;
    private volatile Process process;
    private volatile boolean cancelled;

    public RunningHandle(long runId, long pipelineId) {
        this.runId = runId;
        this.pipelineId = pipelineId;
    }

    public long getRunId() {
        return runId;
    }

    public long getPipelineId() {
        return pipelineId;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void attach(Process process) {
        this.process = process;
    }

    /**
     * 请求停止:置取消标志并立即结束正在运行的子进程.
     */
    public void cancel() {
        cancelled = true;
        Process current = process;
        if (current != null) {
            ProcessKiller.killTree(current);
        }
    }
}
