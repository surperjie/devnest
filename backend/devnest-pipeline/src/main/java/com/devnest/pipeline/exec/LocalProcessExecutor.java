package com.devnest.pipeline.exec;

import com.devnest.pipeline.constant.PipelineConst;
import com.devnest.pipeline.run.RunningHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本机进程执行器.
 * - stdout/stderr 合并(redirectErrorStream),逐行捕获写入 UTF-8 日志文件并积累文本
 * - 后台虚拟线程读管道,避免子进程输出写满阻塞
 * - 支持超时与用户停止(杀进程树)
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
@Component
public class LocalProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(LocalProcessExecutor.class);
    /** bat 文件按简体中文 Windows 默认代码页 936 关联的 GB18030 写入 */
    private static final Charset BAT_FILE_CHARSET = Charset.forName("GB18030");
    private static final Pattern KEY_VALUE = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");

    /**
     * 执行单个脚本步骤.
     *
     * @param spec       渲染后的指令
     * @param handle     运行句柄(停止感知/挂载进程)
     * @param outCharset 子进程输出解码字符集
     */
    public ExecResult run(CommandSpec spec, RunningHandle handle, Charset outCharset) {
        Path logFile = spec.runDir().resolve("step-" + spec.seq() + ".log");
        try {
            List<String> command = buildCommand(spec);
            Path workdirPath = resolveWorkdir(spec);
            if (workdirPath == null) {
                return ExecResult.ofError("工作目录不存在: " + spec.workdir());
            }
            log.info("[pipeline] step{} 执行命令: {}", spec.seq(), String.join(" ", maskArgs(command)));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(workdirPath.toFile());
            Process process = pb.start();
            handle.attach(process);

            OutputCollector collector = new OutputCollector(PipelineConst.CTX_STDOUT_CAP, spec.parseMode());
            Thread reader = Thread.ofVirtual().start(() -> pump(process, logFile, outCharset, collector));

            boolean finished = waitFor(process, handle, spec.timeoutSec());
            if (!finished) {
                ProcessKiller.killTree(process);
            }
            // 等读取线程把剩余输出落盘
            try {
                reader.join(3000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            int exitCode = -1;
            String extra = null;
            try {
                if (finished) {
                    exitCode = process.exitValue();
                }
            } catch (IllegalThreadStateException ignored) {
                // 进程被强杀后未回收
            }
            if (handle.isCancelled()) {
                extra = "已手动停止";
            } else if (!finished) {
                extra = "执行超时(超过 " + spec.timeoutSec() + " 秒)";
            }
            log.info("[pipeline] step{} 退出码: {} {}", spec.seq(), exitCode, extra == null ? "" : extra);
            return new ExecResult(collector.text(), collector.parsed(), exitCode,
                    !finished && !handle.isCancelled(), handle.isCancelled(), extra);
        } catch (Exception e) {
            String msg = friendlyMessage(spec.runType(), e);
            log.warn("[pipeline] step{} 启动失败: {}", spec.seq(), msg);
            // 追加到日志文件,便于前端看到原因
            appendErrorToLog(logFile, msg);
            return ExecResult.ofError(msg);
        }
    }

    private boolean waitFor(Process process, RunningHandle handle, long timeoutSec) {
        long timeoutNanos = timeoutSec > 0 ? TimeUnit.SECONDS.toNanos(timeoutSec) : Long.MAX_VALUE;
        long deadline = System.nanoTime() + timeoutNanos;
        while (true) {
            if (handle.isCancelled()) {
                return false;
            }
            try {
                if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (System.nanoTime() > deadline) {
                return false;
            }
        }
    }

    private List<String> buildCommand(CommandSpec spec) throws Exception {
        Path runDir = spec.runDir();
        List<String> args = spec.args() == null ? List.of() : spec.args();
        return switch (spec.runType()) {
            case PipelineConst.TYPE_BATCH -> {
                Path file = runDir.resolve("step-" + spec.seq() + ".bat");
                // 必须写 CRLF:cmd 按 \r\n 定行,若为纯 LF(Unix)行尾且脚本含多字节内容(如 GB18030 中文),
                // 换代码页/解析时会把后续行的行首字符吞掉,导致命令残缺执行报错
                writeText(file, toCrlf(spec.script()), BAT_FILE_CHARSET, false);
                List<String> cmd = new ArrayList<>();
                cmd.add("cmd.exe");
                cmd.add("/d");
                cmd.add("/s");
                cmd.add("/c");
                cmd.add(file.toString());
                cmd.addAll(args);
                yield cmd;
            }
            case PipelineConst.TYPE_CMDLINE -> List.of("cmd.exe", "/d", "/s", "/c", spec.script());
            case PipelineConst.TYPE_POWERSHELL -> {
                Path file = runDir.resolve("step-" + spec.seq() + ".ps1");
                writeText(file, spec.script(), StandardCharsets.UTF_8, true);
                List<String> cmd = new ArrayList<>();
                cmd.add("powershell.exe");
                cmd.add("-NoProfile");
                cmd.add("-ExecutionPolicy");
                cmd.add("Bypass");
                cmd.add("-File");
                cmd.add(file.toString());
                cmd.addAll(args);
                yield cmd;
            }
            case PipelineConst.TYPE_PYTHON -> {
                Path file = runDir.resolve("step-" + spec.seq() + ".py");
                writeText(file, spec.script(), StandardCharsets.UTF_8, false);
                List<String> cmd = new ArrayList<>();
                cmd.add("python");
                cmd.add("-u");
                cmd.add(file.toString());
                cmd.addAll(args);
                yield cmd;
            }
            default -> throw new IllegalArgumentException("不支持的执行类型: " + spec.runType());
        };
    }

    /** 统一转换为 CRLF 行尾,避免 cmd 解析 .bat 时行首字符丢失 */
    private static String toCrlf(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        return content.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n");
    }

    private void writeText(Path file, String content, Charset charset, boolean withUtf8Bom) throws Exception {
        Files.createDirectories(file.getParent());
        byte[] bom = withUtf8Bom ? new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF} : new byte[0];
        Files.write(file, bom);
        try (BufferedWriter w = Files.newBufferedWriter(file, charset, StandardOpenOption.APPEND)) {
            w.write(content == null ? "" : content);
        }
    }

    private Path resolveWorkdir(CommandSpec spec) {
        String wd = spec.workdir();
        Path dir = (wd == null || wd.isBlank()) ? spec.runDir() : Path.of(wd);
        return Files.isDirectory(dir) ? dir : null;
    }

    private void pump(Process process, Path logFile, Charset charset, OutputCollector collector) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), charset));
             BufferedWriter writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write("\n");
                writer.flush();
                collector.accept(line);
            }
        } catch (Exception e) {
            log.warn("[pipeline] 读取子进程输出失败: {}", e.getMessage());
        }
    }

    private void appendErrorToLog(Path logFile, String message) {
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, "[错误] " + message + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // 日志写不进去不阻塞主流程
        }
    }

    private static String friendlyMessage(String runType, Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("CreateProcess")) {
            return switch (runType) {
                case PipelineConst.TYPE_PYTHON -> "无法启动 python,请确认已安装并加入 PATH";
                case PipelineConst.TYPE_POWERSHELL -> "无法启动 powershell.exe";
                default -> "无法启动进程,请确认可执行文件/解释器已安装并加入 PATH";
            };
        }
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    /** 日志脱敏:只用于日志展示,不真正影响执行 */
    private static List<String> maskArgs(List<String> command) {
        return command;
    }

    /**
     * 有界输出收集器:内存保留后段(截断标识),并可选按行解析 KEY=VALUE.
     */
    private static final class OutputCollector {
        private final int cap;
        private final boolean keyvalue;
        private final StringBuilder text = new StringBuilder();
        private final Map<String, String> parsed = new LinkedHashMap<>();
        private boolean truncated;

        private OutputCollector(int cap, String parseMode) {
            this.cap = cap;
            this.keyvalue = PipelineConst.PARSE_KEYVALUE.equals(parseMode);
        }

        synchronized void accept(String line) {
            if (line.length() > 0) {
                if (keyvalue) {
                    Matcher m = KEY_VALUE.matcher(line);
                    if (m.matches()) {
                        parsed.put(m.group(1), m.group(2));
                    }
                }
            }
            text.append(line).append('\n');
            if (text.length() > cap) {
                truncated = true;
                text.delete(0, text.length() - cap);
            }
        }

        synchronized String text() {
            if (truncated) {
                return "…(输出过长,已截断,完整内容见日志文件)…\n" + text;
            }
            return text.toString();
        }

        synchronized Map<String, String> parsed() {
            return parsed;
        }
    }
}
