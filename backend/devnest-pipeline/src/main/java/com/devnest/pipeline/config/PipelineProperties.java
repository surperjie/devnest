package com.devnest.pipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 流水线运行参数(对应 application.yml 中 devnest.pipeline).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Component
@ConfigurationProperties(prefix = "devnest.pipeline")
public class PipelineProperties {

    /** 运行工作区根目录(相对后端运行目录或绝对路径),每个 run 在其下建独立子目录 */
    private String workspaceDir = "data/pipeline";

    /** 全局最大并发运行数(不同流水线之间并行上限) */
    private int maxConcurrentRuns = 5;

    /**
     * 子进程输出字符集:auto=Windows 用 GB18030,其余平台 UTF-8;
     * 可显式指定如 UTF-8 / GBK / GB18030
     */
    private String outputCharset = "auto";

    public String getWorkspaceDir() {
        return workspaceDir;
    }

    public void setWorkspaceDir(String workspaceDir) {
        this.workspaceDir = workspaceDir;
    }

    public int getMaxConcurrentRuns() {
        return maxConcurrentRuns;
    }

    public void setMaxConcurrentRuns(int maxConcurrentRuns) {
        this.maxConcurrentRuns = maxConcurrentRuns;
    }

    public String getOutputCharset() {
        return outputCharset;
    }

    public void setOutputCharset(String outputCharset) {
        this.outputCharset = outputCharset;
    }
}
