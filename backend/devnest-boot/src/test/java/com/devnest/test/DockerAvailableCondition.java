package com.devnest.test;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Docker 可用性的 JUnit 5 判决条件(配合 {@link EnabledIfDockerAvailable} 使用)。
 *
 * <p>探测结果在类级别缓存:一个测试 JVM 只探测一次,避免每个测试类都去
 * 初始化 Docker 客户端(那会带来可观的启动开销,并且每次失败都要等超时)。
 *
 * @see EnabledIfDockerAvailable
 */
public final class DockerAvailableCondition implements ExecutionCondition {

    private static final String SKIP_REASON =
            "Docker 不可用,跳过 Testcontainers 集成测试。"
                    + "这不是静默降级:该用例已被显式标记为 skipped,"
                    + "CI 的 ubuntu runner 自带 Docker,必须真实执行。"
                    + "如需本地跑通,请启动 Docker Desktop。";

    /** 三重态:null = 尚未探测,避免用 boolean 造成"未探测"与"不可用"无法区分。 */
    private static volatile Boolean dockerAvailable;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (isDockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker 可用");
        }
        return ConditionEvaluationResult.disabled(SKIP_REASON);
    }

    static boolean isDockerAvailable() {
        Boolean cached = dockerAvailable;
        if (cached == null) {
            synchronized (DockerAvailableCondition.class) {
                cached = dockerAvailable;
                if (cached == null) {
                    cached = probeDocker();
                    dockerAvailable = cached;
                }
            }
        }
        return cached;
    }

    private static boolean probeDocker() {
        try {
            boolean available = DockerClientFactory.instance().isDockerAvailable();
            if (!available) {
                System.err.println("[devnest-test] WARN " + SKIP_REASON);
            }
            return available;
        } catch (Throwable t) {
            // 探测本身都可能抛(Docker 未装、socket 无权限等),一律按"不可用"处理并留痕
            System.err.println("[devnest-test] WARN 探测 Docker 时异常,按不可用处理: " + t);
            return false;
        }
    }
}
