package com.devnest.pipeline.exec;

import java.nio.file.Path;
import java.util.List;

/**
 * 单步本机执行指令描述(模板已在引擎层渲染完成).
 *
 * @param runType   batch|cmdline|powershell|python
 * @param script    脚本内容(含渲染后变量)
 * @param args      附加参数(依次作为 %1/%2/$args),已渲染
 * @param workdir   执行工作目录,空则使用运行工作区
 * @param runDir    运行工作区目录(脚本文件与日志落点)
 * @param seq       步骤执行序号(1 起),用于生成 step-{seq}.log
 * @param timeoutSec 超时秒数,<=0 表示不限
 * @param parseMode none|keyvalue
 */
public record CommandSpec(String runType,
                          String script,
                          List<String> args,
                          String workdir,
                          Path runDir,
                          int seq,
                          long timeoutSec,
                          String parseMode) {
}
