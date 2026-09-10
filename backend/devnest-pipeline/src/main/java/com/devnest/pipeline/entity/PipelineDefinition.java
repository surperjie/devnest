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
 * 流水线定义实体.
 * 步骤为子表 pipeline_step(任意数量,seq 升序执行).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Entity
@Table(name = "pipeline_definition")
@Getter
@Setter
public class PipelineDefinition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    private String name;

    /** 默认工作目录,可含 {{变量}},步骤未单独配置工作目录时使用 */
    @Column(name = "workdir", length = 512)
    private String workdir;

    @Column(name = "remark", length = 255)
    private String remark;

    /** 流水线默认变量(JSON 对象),步骤内 {{key}} 可在运行入参未传时取到默认值 */
    @Column(name = "vars", columnDefinition = "TEXT")
    private String vars;
}
