package com.devnest.pipeline.api;

import com.devnest.common.response.ApiResult;
import com.devnest.pipeline.dto.PipelineDto;
import com.devnest.pipeline.dto.PipelineLogChunk;
import com.devnest.pipeline.dto.PipelineRequest;
import com.devnest.pipeline.dto.PipelineRunDto;
import com.devnest.pipeline.dto.PipelineRunStartRequest;
import com.devnest.pipeline.service.PipelineRunService;
import com.devnest.pipeline.service.PipelineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流水线 REST 接口.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final PipelineRunService pipelineRunService;

    /* ===== 流水线定义 CRUD ===== */

    @GetMapping("/pipelines")
    public ApiResult<List<PipelineDto>> listPipelines() {
        return ApiResult.ok(pipelineService.list());
    }

    @GetMapping("/pipelines/{id}")
    public ApiResult<PipelineDto> getPipeline(@PathVariable Long id) {
        return ApiResult.ok(pipelineService.get(id));
    }

    /** 导出流水线定义为 JSON 配置(可下载保存,亦可用于另一实例导入) */
    @GetMapping("/pipelines/{id}/export")
    public ApiResult<PipelineRequest> exportPipeline(@PathVariable Long id) {
        return ApiResult.ok(pipelineService.exportDefinition(id));
    }

    /** 导入流水线定义(请求体即导出文件的 JSON 内容) */
    @PostMapping("/pipelines/import")
    public ApiResult<PipelineDto> importPipeline(@Valid @RequestBody PipelineRequest request) {
        return ApiResult.ok(pipelineService.importDefinition(request));
    }

    @PostMapping("/pipelines")
    public ApiResult<PipelineDto> createPipeline(@Valid @RequestBody PipelineRequest request) {
        return ApiResult.ok(pipelineService.create(request));
    }

    @PutMapping("/pipelines/{id}")
    public ApiResult<PipelineDto> updatePipeline(@PathVariable Long id,
                                                 @Valid @RequestBody PipelineRequest request) {
        return ApiResult.ok(pipelineService.update(id, request));
    }

    @DeleteMapping("/pipelines/{id}")
    public ApiResult<Void> deletePipeline(@PathVariable Long id) {
        pipelineService.delete(id);
        return ApiResult.ok();
    }

    /* ===== 运行 ===== */

    @PostMapping("/pipelines/{id}/runs")
    public ApiResult<PipelineRunDto> startRun(@PathVariable Long id,
                                              @RequestBody(required = false) PipelineRunStartRequest request) {
        return ApiResult.ok(pipelineRunService.start(id,
                request == null ? null : request.getInputs()));
    }

    @GetMapping("/runs")
    public ApiResult<List<PipelineRunDto>> listRuns(@RequestParam(required = false) Long pipelineId) {
        return ApiResult.ok(pipelineRunService.list(pipelineId));
    }

    @GetMapping("/runs/{runId}")
    public ApiResult<PipelineRunDto> getRun(@PathVariable Long runId) {
        return ApiResult.ok(pipelineRunService.detail(runId));
    }

    @PostMapping("/runs/{runId}/stop")
    public ApiResult<Void> stopRun(@PathVariable Long runId) {
        pipelineRunService.stop(runId);
        return ApiResult.ok();
    }

    @GetMapping("/runs/{runId}/steps/{seq}/log")
    public ApiResult<PipelineLogChunk> getStepLog(@PathVariable Long runId,
                                                  @PathVariable Integer seq,
                                                  @RequestParam(defaultValue = "0") long offset) {
        return ApiResult.ok(pipelineRunService.log(runId, seq, offset));
    }
}
