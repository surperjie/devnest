package com.devnest.pipeline.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 启动运行请求:可选的运行入参,会作为上下文初始变量供 {{xxx}} 引用.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Data
public class PipelineRunStartRequest {

    /** 运行入参,如 {"tag":"DTB_WEB_597593_44_fangjie","fileList":"wgt_o2o_web_file_list.txt"} */
    private Map<String, String> inputs = new LinkedHashMap<>();
}
