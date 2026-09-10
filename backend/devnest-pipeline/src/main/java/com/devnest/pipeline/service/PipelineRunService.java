package com.devnest.pipeline.service;

import com.devnest.pipeline.dto.PipelineLogChunk;
import com.devnest.pipeline.dto.PipelineRunDto;

import java.util.List;
import java.util.Map;

/**
 * 流水线运行服务.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public interface PipelineRunService {

    /** 启动运行;同流水线有活动运行则拒绝(DB 行锁 + 内存双保险) */
    PipelineRunDto start(Long pipelineId, Map<String, String> inputs);

    /** 停止运行(仅活动状态可停) */
    void stop(Long runId);

    /** 运行记录列表,pipelineId 为空返回全部(降序,最多 200 条) */
    List<PipelineRunDto> list(Long pipelineId);

    /** 运行详情(含步骤运行列表) */
    PipelineRunDto detail(Long runId);

    /** 步骤日志增量拉取 */
    PipelineLogChunk log(Long runId, Integer seq, long offset);
}
