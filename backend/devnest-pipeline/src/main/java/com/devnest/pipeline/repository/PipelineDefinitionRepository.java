package com.devnest.pipeline.repository;

import com.devnest.pipeline.entity.PipelineDefinition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 流水线定义仓储.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public interface PipelineDefinitionRepository extends JpaRepository<PipelineDefinition, Long> {

    boolean existsByName(String name);

    /**
     * 行级悲观锁查询:启动运行前对流水线定义行加锁,串行化"同流水线并发启动"判断.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PipelineDefinition d where d.id = :id")
    Optional<PipelineDefinition> findByIdForUpdate(@Param("id") Long id);
}
