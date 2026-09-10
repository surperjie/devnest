package com.devnest.pipeline.exec;

import java.util.Map;

/**
 * 单步进程执行结果.
 *
 * @param output       捕获的 stdout+stderr 合并文本(有截断上限)
 * @param parsedOutput 按步骤 output_parse 解析出的键值结果
 * @param exitCode     退出码;-1 表示未正常退出(超时/取消/启动失败)
 * @param timedOut     是否超时
 * @param cancelled    是否被用户停止
 * @param errorMessage 启动失败/超时/取消时的说明
 */
public record ExecResult(String output,
                         Map<String, String> parsedOutput,
                         int exitCode,
                         boolean timedOut,
                         boolean cancelled,
                         String errorMessage) {

    public static ExecResult ofError(String message) {
        return new ExecResult("", Map.of(), -1, false, false, message);
    }

    public boolean ok() {
        return exitCode == 0;
    }
}
