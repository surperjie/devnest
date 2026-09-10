package com.devnest.pipeline.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线创建/更新请求(整条配置,含步骤列表).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Data
public class PipelineRequest {

    @NotBlank(message = "流水线名称不能为空")
    private String name;

    /** 默认工作目录,可含 {{变量}} */
    private String workdir;

    private String remark;

    /** 流水线默认变量(key/value),步骤脚本内 {{key}} 引用;运行入参可同名覆盖 */
    private Map<String, String> vars = new LinkedHashMap<>();

    @NotEmpty(message = "至少配置一个步骤")
    @Valid
    private List<PipelineStepRequest> steps = new ArrayList<>();
}
