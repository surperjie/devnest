package com.devnest.pipeline.service;

import com.devnest.pipeline.dto.PipelineDto;
import com.devnest.pipeline.dto.PipelineRequest;

import java.util.List;

/**
 * 流水线定义服务.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public interface PipelineService {

    List<PipelineDto> list();

    PipelineDto get(Long id);

    /**
     * 导出流水线定义为可再导入的配置(仅含配置字段,不含 id/运行状态等运行时信息).
     */
    PipelineRequest exportDefinition(Long id);

    /**
     * 导入流水线定义(结构同导出格式;名称重复会报错,可由前端改名后重试).
     */
    PipelineDto importDefinition(PipelineRequest request);

    PipelineDto create(PipelineRequest request);

    PipelineDto update(Long id, PipelineRequest request);

    void delete(Long id);
}
