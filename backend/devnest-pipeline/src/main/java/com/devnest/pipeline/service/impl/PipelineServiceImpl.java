package com.devnest.pipeline.service.impl;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.pipeline.constant.PipelineConst;
import com.devnest.pipeline.dto.PipelineDto;
import com.devnest.pipeline.dto.PipelineRequest;
import com.devnest.pipeline.dto.PipelineStepDto;
import com.devnest.pipeline.dto.PipelineStepRequest;
import com.devnest.pipeline.entity.PipelineDefinition;
import com.devnest.pipeline.entity.PipelineRun;
import com.devnest.pipeline.entity.PipelineStep;
import com.devnest.pipeline.repository.PipelineDefinitionRepository;
import com.devnest.pipeline.repository.PipelineRunRepository;
import com.devnest.pipeline.repository.PipelineStepRepository;
import com.devnest.pipeline.repository.PipelineStepRunRepository;
import com.devnest.pipeline.service.PipelineService;
import com.devnest.pipeline.util.PipelineJson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流水线定义服务实现.
 * 步骤整体替换保存;流水线有活动运行时禁止更新/删除.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Service
@RequiredArgsConstructor
public class PipelineServiceImpl implements PipelineService {

    private final PipelineDefinitionRepository definitionRepository;
    private final PipelineStepRepository stepRepository;
    private final PipelineRunRepository runRepository;
    private final PipelineStepRunRepository stepRunRepository;

    @Override
    public List<PipelineDto> list() {
        List<PipelineDefinition> definitions = definitionRepository.findAll().stream()
                .sorted(Comparator.comparing(PipelineDefinition::getId).reversed())
                .toList();
        Map<Long, List<PipelineStep>> stepsByPipeline = stepRepository.findAll().stream()
                .collect(Collectors.groupingBy(PipelineStep::getPipelineId));
        List<PipelineRun> runs = runRepository.findAllByOrderByIdDesc();
        Map<Long, PipelineRun> lastRunByPipeline = new HashMap<>();
        for (PipelineRun run : runs) {
            lastRunByPipeline.putIfAbsent(run.getPipelineId(), run);
        }
        // 活动运行判定(可能存在 WAITING 排队)
        Map<Long, Boolean> activeByPipeline = new HashMap<>();
        runs.stream()
                .filter(r -> PipelineConst.RUN_ACTIVE.contains(r.getStatus()))
                .forEach(r -> activeByPipeline.put(r.getPipelineId(), true));

        return definitions.stream()
                .map(def -> {
                    PipelineDto dto = new PipelineDto();
                    dto.setId(def.getId());
                    dto.setName(def.getName());
                    dto.setWorkdir(def.getWorkdir());
                    dto.setRemark(def.getRemark());
                    dto.setVars(PipelineJson.parseStrMap(def.getVars()));
                    List<PipelineStep> steps = stepsByPipeline.getOrDefault(def.getId(), List.of());
                    dto.setSteps(steps.stream().map(this::toStepDto).toList());
                    dto.setStepCount((int) steps.stream()
                            .filter(s -> s.getEnabled() != null && s.getEnabled() == 1).count());
                    dto.setRunning(Boolean.TRUE.equals(activeByPipeline.get(def.getId())));
                    PipelineRun last = lastRunByPipeline.get(def.getId());
                    if (last != null) {
                        dto.setLastStatus(last.getStatus());
                        dto.setLastEndTime(last.getEndTime() != null ? last.getEndTime() : last.getStartTime());
                    }
                    dto.setCreateTime(def.getCreateTime());
                    dto.setUpdateTime(def.getUpdateTime());
                    return dto;
                })
                .toList();
    }

    @Override
    public PipelineDto get(Long id) {
        PipelineDefinition def = findDefinition(id);
        return toDetailDto(def);
    }

    @Override
    public PipelineRequest exportDefinition(Long id) {
        PipelineDefinition def = findDefinition(id);
        PipelineRequest req = new PipelineRequest();
        req.setName(def.getName());
        req.setWorkdir(def.getWorkdir());
        req.setRemark(def.getRemark());
        req.setVars(PipelineJson.parseStrMap(def.getVars()));
        req.setSteps(stepRepository.findByPipelineIdOrderBySeqAsc(def.getId()).stream()
                .map(this::toStepRequest)
                .toList());
        return req;
    }

    @Override
    @Transactional
    public PipelineDto importDefinition(PipelineRequest request) {
        // 复用创建逻辑:含名称去重校验,同名会抛 PIPELINE_NAME_DUPLICATED
        return create(request);
    }

    @Override
    @Transactional
    public PipelineDto create(PipelineRequest request) {
        if (definitionRepository.existsByName(request.getName())) {
            throw new BizException(ErrorCode.PIPELINE_NAME_DUPLICATED, request.getName());
        }
        PipelineDefinition def = new PipelineDefinition();
        def.setName(request.getName().trim());
        def.setWorkdir(trimToNull(request.getWorkdir()));
        def.setRemark(trimToNull(request.getRemark()));
        def.setVars(toVarsJson(request.getVars()));
        definitionRepository.save(def);
        replaceSteps(def.getId(), request);
        return toDetailDto(def);
    }

    @Override
    @Transactional
    public PipelineDto update(Long id, PipelineRequest request) {
        PipelineDefinition def = findDefinition(id);
        assertNotRunning(def.getId());
        if (!def.getName().equals(request.getName())
                && definitionRepository.existsByName(request.getName())) {
            throw new BizException(ErrorCode.PIPELINE_NAME_DUPLICATED, request.getName());
        }
        def.setName(request.getName().trim());
        def.setWorkdir(trimToNull(request.getWorkdir()));
        def.setRemark(trimToNull(request.getRemark()));
        def.setVars(toVarsJson(request.getVars()));
        definitionRepository.save(def);
        stepRepository.deleteByPipelineId(def.getId());
        replaceSteps(def.getId(), request);
        return toDetailDto(def);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PipelineDefinition def = findDefinition(id);
        assertNotRunning(def.getId());
        List<Long> runIds = runRepository.findByPipelineIdOrderByIdDesc(def.getId()).stream()
                .map(PipelineRun::getId)
                .toList();
        if (!runIds.isEmpty()) {
            stepRunRepository.deleteByRunIdIn(runIds);
            runRepository.deleteByPipelineId(def.getId());
        }
        stepRepository.deleteByPipelineId(def.getId());
        definitionRepository.delete(def);
    }

    private void replaceSteps(Long pipelineId, PipelineRequest request) {
        List<PipelineStep> steps = new ArrayList<>();
        int seq = 0;
        for (PipelineStepRequest req : request.getSteps()) {
            seq++;
            PipelineStep step = new PipelineStep();
            step.setPipelineId(pipelineId);
            step.setSeq(seq);
            step.setName(req.getName().trim());
            step.setRunType(normalizeRunType(req.getRunType()));
            step.setScript(req.getScript() == null ? "" : req.getScript());
            step.setWorkdir(trimToNull(req.getWorkdir()));
            step.setArgs(req.getArgs() == null || req.getArgs().isEmpty()
                    ? null : PipelineJson.toJson(req.getArgs()));
            step.setTimeoutSec(req.getTimeoutSec() == null || req.getTimeoutSec() <= 0 ? 300 : req.getTimeoutSec());
            step.setOnFail(PipelineConst.ON_FAIL_ABORT.equals(req.getOnFail())
                    ? PipelineConst.ON_FAIL_ABORT : PipelineConst.ON_FAIL_CONTINUE);
            step.setOutputParse(PipelineConst.PARSE_KEYVALUE.equals(req.getOutputParse())
                    ? PipelineConst.PARSE_KEYVALUE : PipelineConst.PARSE_NONE);
            step.setEnabled(Boolean.FALSE.equals(req.getEnabled()) ? 0 : 1);
            steps.add(step);
        }
        stepRepository.saveAll(steps);
    }

    private PipelineDto toDetailDto(PipelineDefinition def) {
        PipelineDto dto = new PipelineDto();
        dto.setId(def.getId());
        dto.setName(def.getName());
        dto.setWorkdir(def.getWorkdir());
        dto.setRemark(def.getRemark());
        dto.setVars(PipelineJson.parseStrMap(def.getVars()));
        List<PipelineStep> steps = stepRepository.findByPipelineIdOrderBySeqAsc(def.getId());
        dto.setSteps(steps.stream().map(this::toStepDto).toList());
        dto.setStepCount((int) steps.stream()
                .filter(s -> s.getEnabled() != null && s.getEnabled() == 1).count());
        dto.setRunning(runRepository.existsByPipelineIdAndStatusIn(def.getId(), PipelineConst.RUN_ACTIVE));
        dto.setCreateTime(def.getCreateTime());
        dto.setUpdateTime(def.getUpdateTime());
        return dto;
    }

    private PipelineStepRequest toStepRequest(PipelineStep step) {
        PipelineStepRequest req = new PipelineStepRequest();
        req.setName(step.getName());
        req.setRunType(step.getRunType());
        req.setScript(step.getScript());
        req.setWorkdir(step.getWorkdir());
        req.setArgs(PipelineJson.parseStrList(step.getArgs()));
        req.setTimeoutSec(step.getTimeoutSec());
        req.setOnFail(step.getOnFail());
        req.setOutputParse(step.getOutputParse());
        req.setEnabled(step.getEnabled() == null || step.getEnabled() == 1);
        return req;
    }

    private PipelineStepDto toStepDto(PipelineStep step) {
        PipelineStepDto dto = new PipelineStepDto();
        dto.setId(step.getId());
        dto.setPipelineId(step.getPipelineId());
        dto.setSeq(step.getSeq());
        dto.setName(step.getName());
        dto.setRunType(step.getRunType());
        dto.setScript(step.getScript());
        dto.setWorkdir(step.getWorkdir());
        dto.setArgs(PipelineJson.parseStrList(step.getArgs()));
        dto.setTimeoutSec(step.getTimeoutSec());
        dto.setOnFail(step.getOnFail());
        dto.setOutputParse(step.getOutputParse());
        dto.setEnabled(step.getEnabled() == null || step.getEnabled() == 1);
        return dto;
    }

    private PipelineDefinition findDefinition(Long id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.PIPELINE_NOT_FOUND, String.valueOf(id)));
    }

    private void assertNotRunning(Long pipelineId) {
        if (runRepository.existsByPipelineIdAndStatusIn(pipelineId, PipelineConst.RUN_ACTIVE)) {
            throw new BizException(ErrorCode.PIPELINE_ALREADY_RUNNING);
        }
    }

    private static String toVarsJson(Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) {
            return null;
        }
        // 去掉值为 null 的键,统一 String 存储
        Map<String, String> clean = new HashMap<>();
        vars.forEach((k, v) -> {
            if (k != null && !k.isBlank()) {
                clean.put(k.trim(), v == null ? "" : v);
            }
        });
        return clean.isEmpty() ? null : PipelineJson.toJson(clean);
    }

    private static String normalizeRunType(String type) {
        return switch (type) {
            case PipelineConst.TYPE_CMDLINE, PipelineConst.TYPE_POWERSHELL,
                 PipelineConst.TYPE_PYTHON -> type;
            default -> PipelineConst.TYPE_BATCH;
        };
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
