package com.devnest.pipeline.constant;

import java.util.Set;

/**
 * 流水线状态/类型常量.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/9 10:00
 */
public final class PipelineConst {

    private PipelineConst() {
    }

    /* ===== run 状态 ===== */
    public static final String RUN_WAITING = "WAITING";
    public static final String RUN_RUNNING = "RUNNING";
    public static final String RUN_SUCCESS = "SUCCESS";
    public static final String RUN_FAILED = "FAILED";
    public static final String RUN_STOPPED = "STOPPED";
    /** 活动运行:同时只允许同流水线存在一个 */
    public static final Set<String> RUN_ACTIVE = Set.of(RUN_WAITING, RUN_RUNNING);
    /** 终态 */
    public static final Set<String> RUN_TERMINAL = Set.of(RUN_SUCCESS, RUN_FAILED, RUN_STOPPED);

    /* ===== 步骤运行状态 ===== */
    public static final String STEP_RUNNING = "RUNNING";
    public static final String STEP_SUCCESS = "SUCCESS";
    public static final String STEP_FAILED = "FAILED";
    public static final String STEP_SKIPPED = "SKIPPED";

    /* ===== 步骤执行类型 ===== */
    public static final String TYPE_BATCH = "batch";
    public static final String TYPE_CMDLINE = "cmdline";
    public static final String TYPE_POWERSHELL = "powershell";
    public static final String TYPE_PYTHON = "python";

    /* ===== on_fail 策略 ===== */
    public static final String ON_FAIL_ABORT = "abort";
    public static final String ON_FAIL_CONTINUE = "continue";

    /* ===== 输出解析 ===== */
    public static final String PARSE_NONE = "none";
    public static final String PARSE_KEYVALUE = "keyvalue";

    /** stdout 进上下文的字节上限,防内存爆掉 */
    public static final int CTX_STDOUT_CAP = 200_000;
}
