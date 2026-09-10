package com.devnest.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流水线运行实例 DTO.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Data
public class PipelineRunDto {

    private Long id;
    private Long pipelineId;
    private String pipelineName;
    private String status;
    private Map<String, String> inputs;
    private String workspaceDir;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private Long durationSeconds;
    /** 详情接口返回步骤运行;列表接口为 null */
    private List<PipelineStepRunDto> steps;
}
