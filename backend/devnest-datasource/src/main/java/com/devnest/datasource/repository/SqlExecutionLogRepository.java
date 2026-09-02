package com.devnest.datasource.repository;

import com.devnest.datasource.entity.SqlExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
public interface SqlExecutionLogRepository extends JpaRepository<SqlExecutionLog, Long> {
    Page<SqlExecutionLog> findByDatasourceIdOrderByCreateTimeDesc(Long datasourceId, Pageable pageable);
    List<SqlExecutionLog> findTop20ByDatasourceIdOrderByCreateTimeDesc(Long datasourceId);
}
