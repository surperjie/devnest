package com.devnest.pipeline.dto;

/**
 * 步骤日志增量分片:前端用 offset 轮询实现"实时追日志".
 *
 * @param text       本次新增文本
 * @param nextOffset 下一次请求应携带的 offset
 * @param finished   步骤是否已结束(结束且读取到文件尾=true)
 * @param status     步骤当前状态,便于前端判断
 */
public record PipelineLogChunk(String text, long nextOffset, boolean finished, String status) {
}
