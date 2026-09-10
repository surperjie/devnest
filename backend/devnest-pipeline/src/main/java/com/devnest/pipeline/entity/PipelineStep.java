package com.devnest.pipeline.entity;

import com.devnest.core.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 流水线步骤实体.
 * 支持本机执行 batch/cmdline/powershell/python,脚本内容入库,可在前端双击修改.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Entity
@Table(name = "pipeline_step")
@Getter
@Setter
public class PipelineStep extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pipeline_id", nullable = false)
    private Long pipelineId;

    /** 执行顺序,从 1 递增 */
    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** batch|cmdline|powershell|python */
    @Column(name = "run_type", nullable = false, length = 16)
    private String runType = "batch";

    /** 脚本内容/命令行,支持 {{ctx变量}} */
    @Column(name = "script", nullable = false, columnDefinition = "TEXT")
    private String script;

    /** 步骤工作目录,空=继承流水线 workdir 或运行工作区 */
    @Column(name = "workdir", length = 512)
    private String workdir;

    /** 附加参数 JSON 数组(依次作为 %1/%2/$args),可含 {{ctx变量}} */
    @Column(name = "args", columnDefinition = "TEXT")
    private String args;

    @Column(name = "timeout_sec", nullable = false)
    private Integer timeoutSec = 300;

    /** abort|continue */
    @Column(name = "on_fail", nullable = false, length = 16)
    private String onFail = "abort";

    /** none|keyvalue */
    @Column(name = "output_parse", nullable = false, length = 16)
    private String outputParse = "none";

    /** 1=启用,0=跳过 */
    @Column(name = "enabled", nullable = false)
    private Integer enabled = 1;
}
