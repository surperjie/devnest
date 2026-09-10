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

import java.time.LocalDateTime;

/**
 * 流水线运行实例实体.
 * 约束:同一条流水线同时只允许 1 个活动(WAITING/RUNNING)运行.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Entity
@Table(name = "pipeline_run")
@Getter
@Setter
public class PipelineRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pipeline_id", nullable = false)
    private Long pipelineId;

    /** 冗余流水线名,便于定义删除后仍能展示历史 */
    @Column(name = "pipeline_name", nullable = false, length = 64)
    private String pipelineName;

    /** WAITING|RUNNING|SUCCESS|FAILED|STOPPED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 运行入参 JSON 对象(键值),为上下文初始变量 */
    @Column(name = "inputs", columnDefinition = "TEXT")
    private String inputs;

    /** 本次运行工作区目录(绝对路径),脚本文件与日志落点 */
    @Column(name = "workspace_dir", length = 512)
    private String workspaceDir;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;
}
