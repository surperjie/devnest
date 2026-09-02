package com.devnest.datasource.entity;

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
 * SQL 执行日志实体.
 * 记录每次 SQL 执行的语句、数据源、执行时间、结果状态.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Entity
@Table(name = "sql_execution_log")
@Getter
@Setter
public class SqlExecutionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "datasource_id", nullable = false)
    private Long datasourceId;

    @Column(name = "datasource_name", nullable = false, length = 64)
    private String datasourceName;

    @Column(name = "sql_text", nullable = false, columnDefinition = "TEXT")
    private String sqlText;

    /** SUCCESS / FAILED / BLOCKED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    @Column(name = "cost_ms")
    private Long costMs;

    @Column(name = "row_count")
    private Integer rowCount;
}
