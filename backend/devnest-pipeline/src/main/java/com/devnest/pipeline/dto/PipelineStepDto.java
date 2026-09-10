package com.devnest.pipeline.dto;

import lombok.Data;

import java.util.List;

/**
 * 流水线步骤返回 DTO.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Data
public class PipelineStepDto {

    private Long id;
    private Long pipelineId;
    private Integer seq;
    private String name;
    private String runType;
    private String script;
    private String workdir;
    private List<String> args;
    private Integer timeoutSec;
    private String onFail;
    private String outputParse;
    private Boolean enabled;
}
