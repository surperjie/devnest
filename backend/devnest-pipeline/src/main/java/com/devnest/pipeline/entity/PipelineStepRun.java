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
 * 流水线步骤运行实例实体.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Entity
@Table(name = "pipeline_step_run")
@Getter
@Setter
public class PipelineStepRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    /** 执行顺序,从 1 递增 */
    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "run_type", nullable = false, length = 16)
    private String runType;

    /** RUNNING|SUCCESS|FAILED|SKIPPED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;
}
