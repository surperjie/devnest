package com.devnest.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在依赖 Docker 的集成测试上:若 Docker 不可用,该测试被<b>显式跳过</b>。
 *
 * <p><b>为什么不直接用 {@code Assumptions.assumeTrue(...)}</b>:
 * 断言式跳过在报告里只能看到 skipped 计数,看不出原因;而
 * {@link DockerAvailableCondition} 会把"为什么跳过"写进跳过原因,
 * 并在探测失败时向 stderr 打印 WARN —— 满足"允许降级,但不许静默降级"。
 *
 * <p><b>纪律</b>:凡是用 {@link AbstractContainerTest} 的测试类,必须打这个注解。
 * 没有它而 Docker 又不可用时,容器启动会直接抛异常让构建变红 —— 这是刻意的:
 * 宁可红,也不要把"容器没起来"伪装成"测试通过"。
 *
 * @see DockerAvailableCondition
 */
@Documented
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerAvailableCondition.class)
public @interface EnabledIfDockerAvailable {
}
