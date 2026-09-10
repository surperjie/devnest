package com.devnest.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水线步骤创建/更新请求.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Data
public class PipelineStepRequest {

    @NotBlank(message = "步骤名称不能为空")
    private String name;

    /** batch|cmdline|powershell|python */
    @NotBlank(message = "执行类型不能为空")
    private String runType = "batch";

    @NotBlank(message = "脚本内容不能为空")
    private String script;

    /** 步骤工作目录,空=继承流水线 workdir */
    private String workdir;

    /** 附加参数,依次作为 %1/%2/$args */
    private List<String> args = new ArrayList<>();

    private Integer timeoutSec = 300;

    /** abort|continue */
    private String onFail = "abort";

    /** none|keyvalue */
    private String outputParse = "none";

    private Boolean enabled = true;
}
