package com.devnest.pipeline.repository;

import com.devnest.pipeline.entity.PipelineStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

/**
 * 流水线步骤仓储.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public interface PipelineStepRepository extends JpaRepository<PipelineStep, Long> {

    List<PipelineStep> findByPipelineIdOrderBySeqAsc(Long pipelineId);

    @Modifying
    void deleteByPipelineId(Long pipelineId);
}
