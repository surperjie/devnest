package com.devnest.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流水线步骤运行实例 DTO.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Data
public class PipelineStepRunDto {

    private Long id;
    private Long runId;
    private Integer seq;
    private String name;
    private String runType;
    private String status;
    private Integer exitCode;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSeconds;
}
