package com.devnest.pipeline.repository;

import com.devnest.pipeline.entity.PipelineStepRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 流水线步骤运行实例仓储.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public interface PipelineStepRunRepository extends JpaRepository<PipelineStepRun, Long> {

    List<PipelineStepRun> findByRunIdOrderBySeqAsc(Long runId);

    Optional<PipelineStepRun> findByRunIdAndSeq(Long runId, Integer seq);

    void deleteByRunIdIn(Collection<Long> runIds);
}
