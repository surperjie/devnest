package com.devnest.pipeline.service.impl;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.pipeline.constant.PipelineConst;
import com.devnest.pipeline.dto.PipelineLogChunk;
import com.devnest.pipeline.dto.PipelineRunDto;
import com.devnest.pipeline.dto.PipelineStepRunDto;
import com.devnest.pipeline.entity.PipelineDefinition;
import com.devnest.pipeline.entity.PipelineRun;
import com.devnest.pipeline.entity.PipelineStepRun;
import com.devnest.pipeline.repository.PipelineDefinitionRepository;
import com.devnest.pipeline.repository.PipelineRunRepository;
import com.devnest.pipeline.repository.PipelineStepRunRepository;
import com.devnest.pipeline.run.PipelineRunManager;
import com.devnest.pipeline.service.PipelineRunService;
import com.devnest.pipeline.util.PipelineJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 流水线运行服务实现.
 * 启动:对定义行加悲观锁 → 校验无活动运行 → 落库 WAITING → 事务提交后提交到运行管理器.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Service
public class PipelineRunServiceImpl implements PipelineRunService {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunServiceImpl.class);
    private static final long MAX_RUN_LIST = 200;
    private static final long MAX_LOG_FILE_BYTES = 50 * 1024 * 1024;

    private final PipelineDefinitionRepository definitionRepository;
    private final PipelineRunRepository runRepository;
    private final PipelineStepRunRepository stepRunRepository;
    private final PipelineRunManager runManager;

    public PipelineRunServiceImpl(PipelineDefinitionRepository definitionRepository,
                                  PipelineRunRepository runRepository,
                                  PipelineStepRunRepository stepRunRepository,
                                  PipelineRunManager runManager) {
        this.definitionRepository = definitionRepository;
        this.runRepository = runRepository;
        this.stepRunRepository = stepRunRepository;
        this.runManager = runManager;
    }

    @Override
    @Transactional
    public PipelineRunDto start(Long pipelineId, Map<String, String> inputs) {
        // 行级悲观锁:串行化同流水线的并发启动
        PipelineDefinition definition = definitionRepository.findByIdForUpdate(pipelineId)
                .orElseThrow(() -> new BizException(ErrorCode.PIPELINE_NOT_FOUND, String.valueOf(pipelineId)));
        if (runManager.isPipelineRunning(pipelineId)
                || runRepository.existsByPipelineIdAndStatusIn(pipelineId, PipelineConst.RUN_ACTIVE)) {
            throw new BizException(ErrorCode.PIPELINE_ALREADY_RUNNING);
        }

        PipelineRun run = new PipelineRun();
        run.setPipelineId(definition.getId());
        run.setPipelineName(definition.getName());
        run.setStatus(PipelineConst.RUN_WAITING);
        Map<String, String> safeInputs = new LinkedHashMap<>();
        if (inputs != null) {
            inputs.forEach((k, v) -> safeInputs.put(k, v == null ? "" : v));
        }
        run.setInputs(PipelineJson.toJson(safeInputs));
        runRepository.saveAndFlush(run);
        final long runId = run.getId();

        // 等当前事务提交后再提交执行,避免引擎读不到未提交的 WAITING 行
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean accepted = runManager.submit(runId, pipelineId);
                if (!accepted) {
                    log.warn("[pipeline] 提交 run {} 被拒(内存并发保护),标记为停止", runId);
                    runRepository.findById(runId).ifPresent(r -> {
                        r.setStatus(PipelineConst.RUN_STOPPED);
                        r.setErrorMessage("并发保护触发,未执行");
                        r.setEndTime(LocalDateTime.now());
                        runRepository.save(r);
                    });
                }
            }
        });
        log.info("[pipeline] 流水线 {} 启动 run {},入参键: {}", definition.getName(), runId, safeInputs.keySet());
        return toDto(run, false);
    }

    @Override
    @Transactional
    public void stop(Long runId) {
        PipelineRun run = runRepository.findById(runId)
                .orElseThrow(() -> new BizException(ErrorCode.PIPELINE_RUN_NOT_FOUND, String.valueOf(runId)));
        if (!PipelineConst.RUN_ACTIVE.contains(run.getStatus())) {
            throw new BizException(ErrorCode.PIPELINE_RUN_NOT_ACTIVE);
        }
        if (!runManager.stop(runId)) {
            // 句柄已消失(极端情况):直接落库终态,引擎如仍在执行会自行收敛
            run.setStatus(PipelineConst.RUN_STOPPED);
            run.setErrorMessage("已手动停止");
            run.setEndTime(LocalDateTime.now());
            runRepository.save(run);
        }
    }

    @Override
    public List<PipelineRunDto> list(Long pipelineId) {
        List<PipelineRun> runs = pipelineId == null
                ? runRepository.findAllByOrderByIdDesc()
                : runRepository.findByPipelineIdOrderByIdDesc(pipelineId);
        return runs.stream()
                .limit(MAX_RUN_LIST)
                .map(r -> toDto(r, false))
                .toList();
    }

    @Override
    public PipelineRunDto detail(Long runId) {
        PipelineRun run = runRepository.findById(runId)
                .orElseThrow(() -> new BizException(ErrorCode.PIPELINE_RUN_NOT_FOUND, String.valueOf(runId)));
        return toDto(run, true);
    }

    @Override
    public PipelineLogChunk log(Long runId, Integer seq, long offset) {
        PipelineRun run = runRepository.findById(runId)
                .orElseThrow(() -> new BizException(ErrorCode.PIPELINE_RUN_NOT_FOUND, String.valueOf(runId)));
        String status = null;
        Optional<PipelineStepRun> stepRun = stepRunRepository.findByRunIdAndSeq(runId, seq);
        if (stepRun.isPresent()) {
            status = stepRun.get().getStatus();
        }
        String text = "";
        boolean finished = status != null && !PipelineConst.STEP_RUNNING.equals(status);
        if (run.getWorkspaceDir() != null) {
            Path logFile = Path.of(run.getWorkspaceDir(), "step-" + seq + ".log");
            if (Files.isRegularFile(logFile)) {
                try {
                    long size = Files.size(logFile);
                    if (size > MAX_LOG_FILE_BYTES) {
                        text = "\n[提示] 日志文件过大(" + (size / 1024 / 1024) + "MB),已停止展示,请直接查看文件系统\n";
                        offset = text.length();
                        finished = true;
                    } else {
                        String full = Files.readString(logFile, StandardCharsets.UTF_8);
                        long len = full.length();
                        if (offset > len) {
                            offset = len;
                        }
                        text = full.substring((int) offset);
                        offset = len;
                        // 步骤已结束且文件没有新增 → 判定读完
                        if (finished && offset >= len) {
                            finished = true;
                        } else if (status != null && PipelineConst.STEP_RUNNING.equals(status)) {
                            finished = false;
                        }
                    }
                } catch (Exception e) {
                    text = "\n[错误] 读取日志失败: " + e.getMessage() + "\n";
                    finished = true;
                }
            } else {
                // 文件尚未创建:WAITING/刚启动
                finished = status != null && PipelineConst.STEP_RUNNING.equals(status) ? false
                        : (status == null ? true : !PipelineConst.RUN_ACTIVE.contains(run.getStatus()));
            }
        }
        return new PipelineLogChunk(text, offset, finished,
                status == null ? run.getStatus() : status);
    }

    private PipelineRunDto toDto(PipelineRun run, boolean withSteps) {
        PipelineRunDto dto = new PipelineRunDto();
        dto.setId(run.getId());
        dto.setPipelineId(run.getPipelineId());
        dto.setPipelineName(run.getPipelineName());
        dto.setStatus(run.getStatus());
        dto.setInputs(PipelineJson.parseStrMap(run.getInputs()));
        dto.setWorkspaceDir(run.getWorkspaceDir());
        dto.setErrorMessage(run.getErrorMessage());
        dto.setStartTime(run.getStartTime());
        dto.setEndTime(run.getEndTime());
        dto.setCreateTime(run.getCreateTime());
        dto.setDurationSeconds(durationSeconds(run));
        if (withSteps) {
            dto.setSteps(stepRunRepository.findByRunIdOrderBySeqAsc(run.getId()).stream()
                    .map(this::toStepRunDto)
                    .toList());
        }
        return dto;
    }

    private PipelineStepRunDto toStepRunDto(PipelineStepRun stepRun) {
        PipelineStepRunDto dto = new PipelineStepRunDto();
        dto.setId(stepRun.getId());
        dto.setRunId(stepRun.getRunId());
        dto.setSeq(stepRun.getSeq());
        dto.setName(stepRun.getName());
        dto.setRunType(stepRun.getRunType());
        dto.setStatus(stepRun.getStatus());
        dto.setExitCode(stepRun.getExitCode());
        dto.setErrorMessage(stepRun.getErrorMessage());
        dto.setStartTime(stepRun.getStartTime());
        dto.setEndTime(stepRun.getEndTime());
        if (stepRun.getStartTime() != null) {
            LocalDateTime end = stepRun.getEndTime() != null
                    ? stepRun.getEndTime() : LocalDateTime.now();
            dto.setDurationSeconds(Duration.between(stepRun.getStartTime(), end).getSeconds());
        }
        return dto;
    }

    private static Long durationSeconds(PipelineRun run) {
        if (run.getStartTime() == null) {
            return null;
        }
        LocalDateTime end = run.getEndTime() != null ? run.getEndTime() : LocalDateTime.now();
        return Duration.between(run.getStartTime(), end).getSeconds();
    }
}
