package com.devnest.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流水线定义返回 DTO.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Data
public class PipelineDto {

    private Long id;
    private String name;
    private String workdir;
    private String remark;
    /** 流水线默认变量(key/value),空为未配置 */
    private Map<String, String> vars;
    private List<PipelineStepDto> steps;
    private Integer stepCount;
    /** 是否正在运行(供列表实时展示/禁用运行按钮) */
    private Boolean running;
    /** 最近一次运行结果,无历史为 null */
    private String lastStatus;
    private LocalDateTime lastEndTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
