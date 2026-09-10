package com.devnest.pipeline.run;

import com.devnest.pipeline.config.PipelineProperties;
import com.devnest.pipeline.constant.PipelineConst;
import com.devnest.pipeline.entity.PipelineDefinition;
import com.devnest.pipeline.entity.PipelineRun;
import com.devnest.pipeline.entity.PipelineStep;
import com.devnest.pipeline.entity.PipelineStepRun;
import com.devnest.pipeline.exec.CommandSpec;
import com.devnest.pipeline.exec.ExecResult;
import com.devnest.pipeline.exec.LocalProcessExecutor;
import com.devnest.pipeline.repository.PipelineDefinitionRepository;
import com.devnest.pipeline.repository.PipelineRunRepository;
import com.devnest.pipeline.repository.PipelineStepRepository;
import com.devnest.pipeline.repository.PipelineStepRunRepository;
import com.devnest.pipeline.util.PipelineJson;
import com.devnest.pipeline.util.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线执行引擎:按 seq 顺序在虚拟线程中执行启用的步骤.
 * - 步骤间串行,上下文共享(上一步 stdout/解析结果给下一步用)
 * - 每步独立子进程(超时/可停止/杀进程树),输出落 run 工作区 step-{seq}.log
 * - 同流水线并发已由服务层+管理器双重禁止
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Component
public class PipelineExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionEngine.class);

    private final PipelineProperties properties;
    private final PipelineDefinitionRepository definitionRepository;
    private final PipelineStepRepository stepRepository;
    private final PipelineRunRepository runRepository;
    private final PipelineStepRunRepository stepRunRepository;
    private final LocalProcessExecutor processExecutor;

    public PipelineExecutionEngine(PipelineProperties properties,
                                   PipelineDefinitionRepository definitionRepository,
                                   PipelineStepRepository stepRepository,
                                   PipelineRunRepository runRepository,
                                   PipelineStepRunRepository stepRunRepository,
                                   LocalProcessExecutor processExecutor) {
        this.properties = properties;
        this.definitionRepository = definitionRepository;
        this.stepRepository = stepRepository;
        this.runRepository = runRepository;
        this.stepRunRepository = stepRunRepository;
        this.processExecutor = processExecutor;
    }

    public void execute(long runId, RunningHandle handle) {
        PipelineRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            log.warn("[pipeline] run {} 不存在,引擎直接退出", runId);
            return;
        }
        PipelineDefinition definition = definitionRepository.findById(run.getPipelineId()).orElse(null);
        if (definition == null) {
            failRun(run, "流水线定义不存在或已被删除");
            return;
        }
        if (handle.isCancelled()) {
            stopRun(run, "运行在排队期间被停止");
            return;
        }

        Path runDir;
        try {
            runDir = createRunWorkspace(run);
        } catch (IOException e) {
            log.warn("[pipeline] run {} 创建工作区失败: {}", runId, e.getMessage());
            failRun(run, "创建运行工作区失败: " + e.getMessage());
            return;
        }

        List<PipelineStep> steps = stepRepository.findByPipelineIdOrderBySeqAsc(definition.getId()).stream()
                .filter(s -> s.getEnabled() != null && s.getEnabled() == 1)
                .toList();
        if (steps.isEmpty()) {
            failRun(run, "未配置任何启用的步骤");
            return;
        }

        run.setStatus(PipelineConst.RUN_RUNNING);
        run.setStartTime(LocalDateTime.now());
        run.setWorkspaceDir(runDir.toString());
        run.setErrorMessage(null);
        runRepository.save(run);

        // 上下文合并顺序:流水线默认变量 -> 本次运行入参(同名覆盖) -> 内置 run.*
        Map<String, String> ctx = new HashMap<>(PipelineJson.parseStrMap(definition.getVars()));
        ctx.putAll(PipelineJson.parseStrMap(run.getInputs()));
        ctx.put("run.id", String.valueOf(runId));
        ctx.put("run.workspace", runDir.toString());
        ctx.put("run.pipeline", definition.getName());

        boolean anyFailed = false;
        String firstError = null;
        int seq = 0;
        for (PipelineStep step : steps) {
            seq++;
            if (handle.isCancelled()) {
                break;
            }
            PipelineStepRun stepRun = beginStep(runId, seq, step);
            writeStepHeader(runDir, seq, step, stepRun);
            try {
                CommandSpec spec = buildSpec(step, definition, ctx, runDir, seq);
                ExecResult result = processExecutor.run(spec, handle, outputCharset());
                stepRun.setExitCode(result.exitCode());
                if (result.ok()) {
                    stepRun.setStatus(PipelineConst.STEP_SUCCESS);
                    mergeStepResult(ctx, seq, result, step);
                } else {
                    stepRun.setStatus(PipelineConst.STEP_FAILED);
                    String message = failureMessage(result);
                    stepRun.setErrorMessage(truncate(message));
                    if (firstError == null) {
                        firstError = message;
                    }
                    anyFailed = true;
                }
                if (result.cancelled()) {
                    break;
                }
            } catch (Exception e) {
                log.warn("[pipeline] run {} step{} 异常: {}", runId, seq, e.getMessage());
                stepRun.setStatus(PipelineConst.STEP_FAILED);
                stepRun.setErrorMessage(truncate(e.getMessage()));
                anyFailed = true;
                if (firstError == null) {
                    firstError = "步骤" + seq + "「" + step.getName() + "」异常: " + e.getMessage();
                }
                if (PipelineConst.ON_FAIL_ABORT.equals(step.getOnFail())) {
                    stepRun.setEndTime(LocalDateTime.now());
                    stepRunRepository.save(stepRun);
                    break;
                }
            }
            stepRun.setEndTime(LocalDateTime.now());
            stepRunRepository.save(stepRun);

            // 失败策略
            if (stepRun.getStatus() != null && PipelineConst.STEP_FAILED.equals(stepRun.getStatus())
                    && PipelineConst.ON_FAIL_ABORT.equals(step.getOnFail())) {
                break;
            }
        }

        // 收尾
        if (handle.isCancelled()) {
            stopRun(run, firstError != null ? firstError : "已手动停止");
        } else if (anyFailed) {
            failRun(run, firstError);
        } else {
            run.setStatus(PipelineConst.RUN_SUCCESS);
            run.setErrorMessage(null);
            run.setEndTime(LocalDateTime.now());
            runRepository.save(run);
        }
        log.info("[pipeline] run {} 结束,状态: {}", runId, run.getStatus());
    }

    private CommandSpec buildSpec(PipelineStep step, PipelineDefinition definition,
                                  Map<String, String> ctx, Path runDir, int seq) {
        String script = TemplateRenderer.render(step.getScript(), ctx);
        List<String> rawArgs = PipelineJson.parseStrList(step.getArgs());
        List<String> args = rawArgs.stream()
                .map(a -> TemplateRenderer.render(a, ctx))
                .toList();
        String stepWorkdir = TemplateRenderer.render(step.getWorkdir(), ctx);
        String effectiveWorkdir = isBlank(stepWorkdir)
                ? TemplateRenderer.render(definition.getWorkdir(), ctx)
                : stepWorkdir;
        return new CommandSpec(
                safeRunType(step.getRunType()),
                script,
                args,
                isBlank(effectiveWorkdir) ? null : effectiveWorkdir,
                runDir,
                seq,
                step.getTimeoutSec() != null ? step.getTimeoutSec() : 300L,
                step.getOutputParse() == null ? PipelineConst.PARSE_NONE : step.getOutputParse()
        );
    }

    private PipelineStepRun beginStep(long runId, int seq, PipelineStep step) {
        PipelineStepRun stepRun = new PipelineStepRun();
        stepRun.setRunId(runId);
        stepRun.setSeq(seq);
        stepRun.setName(step.getName());
        stepRun.setRunType(safeRunType(step.getRunType()));
        stepRun.setStatus(PipelineConst.STEP_RUNNING);
        stepRun.setStartTime(LocalDateTime.now());
        return stepRunRepository.save(stepRun);
    }

    private void writeStepHeader(Path runDir, int seq, PipelineStep step, PipelineStepRun stepRun) {
        try {
            Path logFile = runDir.resolve("step-" + seq + ".log");
            String line = "==== 步骤" + seq + ": " + step.getName()
                    + " (" + step.getRunType() + ") ====\n";
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("[pipeline] 写入步骤头失败: {}", e.getMessage());
        }
    }

    private void mergeStepResult(Map<String, String> ctx, int seq, ExecResult result, PipelineStep step) {
        if (result.parsedOutput() != null && !result.parsedOutput().isEmpty()) {
            ctx.putAll(result.parsedOutput());
        }
        if (result.output() != null) {
            ctx.put("step" + seq + ".stdout", result.output());
        }
        if (PipelineConst.PARSE_KEYVALUE.equals(step.getOutputParse())) {
            log.info("[pipeline] 步骤{} 解析到 {} 个键值结果", seq,
                    result.parsedOutput() == null ? 0 : result.parsedOutput().size());
        }
    }

    private String failureMessage(ExecResult result) {
        if (result.timedOut()) {
            return "执行超时";
        }
        if (result.cancelled()) {
            return "已手动停止";
        }
        StringBuilder sb = new StringBuilder();
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            sb.append(result.errorMessage());
        } else {
            sb.append("退出码: ").append(result.exitCode());
        }
        return sb.toString();
    }

    private void failRun(PipelineRun run, String message) {
        run.setStatus(PipelineConst.RUN_FAILED);
        run.setErrorMessage(truncate(message));
        run.setEndTime(LocalDateTime.now());
        runRepository.save(run);
    }

    private void stopRun(PipelineRun run, String message) {
        run.setStatus(PipelineConst.RUN_STOPPED);
        run.setErrorMessage(truncate(message));
        run.setEndTime(LocalDateTime.now());
        runRepository.save(run);
    }

    private Path createRunWorkspace(PipelineRun run) throws IOException {
        Path root = Path.of(properties.getWorkspaceDir()).toAbsolutePath().normalize();
        String safeName = sanitize(run.getPipelineName());
        Path dir = root.resolve(safeName + "-" + run.getId());
        Files.createDirectories(dir);
        return dir;
    }

    private Charset outputCharset() {
        String cfg = properties.getOutputCharset();
        if (cfg == null || cfg.isBlank() || "auto".equalsIgnoreCase(cfg.trim())) {
            return isWindows() ? Charset.forName("GB18030") : StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(cfg);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String safeRunType(String runType) {
        if (runType == null || runType.isBlank()) {
            return PipelineConst.TYPE_BATCH;
        }
        return switch (runType) {
            case PipelineConst.TYPE_CMDLINE, PipelineConst.TYPE_POWERSHELL,
                 PipelineConst.TYPE_PYTHON, PipelineConst.TYPE_BATCH -> runType;
            default -> PipelineConst.TYPE_BATCH;
        };
    }

    private static String sanitize(String name) {
        String s = name == null ? "pipeline" : name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (s.length() > 40) {
            s = s.substring(0, 40);
        }
        return s;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
