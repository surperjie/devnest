package com.devnest.pipeline.repository;

import com.devnest.pipeline.entity.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 流水线运行实例仓储.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {

    boolean existsByPipelineIdAndStatusIn(Long pipelineId, Collection<String> statuses);

    List<PipelineRun> findByPipelineIdOrderByIdDesc(Long pipelineId);

    List<PipelineRun> findAllByOrderByIdDesc();

    Optional<PipelineRun> findTopByPipelineIdOrderByIdDesc(Long pipelineId);

    List<PipelineRun> findByStatusIn(Collection<String> statuses);

    void deleteByPipelineId(Long pipelineId);
}
