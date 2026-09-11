package com.devnest.resource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ResourceRegistryTestBase} 的自检。
 *
 * <p>P0 阶段这一步的价值只有一个,但很关键:<b>证明守护载体本身是活的</b>。
 * 一个从来没有真正判过违规的守护机制,和没有守护机制是无法区分的。
 * 所以这里除了"空跑"继承来的用例,还直接测判决逻辑 —— 构造出"预期有、实际没有"
 * 的输入,断言它确实能识别出缺失。
 */
@DisplayName("资源登记守护载体自检")
class ResourceRegistryTestBaseSelfTest extends ResourceRegistryTestBase {

    @Override
    protected Set<String> expectedResourceTypes() {
        // P0 阶段刻意留空:core.resource.ResourceRegistry 尚未建立。
        // 显式写 Set.of() 而不是省略,是为了让人一眼看出"这里是故意为空的",
        // 而不是"忘了重写"。
        return Set.of();
    }

    @Test
    @DisplayName("判决逻辑:预期中有、实际没有 → 判为缺失")
    void 能识别出未登记的资源类型() {
        Set<String> missing = ResourceRegistryTestBase.findMissing(
                Set.of("datasource", "redis-instance"),
                Set.of("datasource"));

        assertThat(missing).containsExactly("redis-instance");
    }

    @Test
    @DisplayName("判决逻辑:预期与实际完全一致 → 无缺失")
    void 预期与登记一致时不报缺失() {
        Set<String> missing = ResourceRegistryTestBase.findMissing(
                Set.of("datasource", "redis-instance"),
                Set.of("datasource", "redis-instance"));

        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("判决逻辑:登记了额外的资源类型 → 不算违规(只查漏,不查多)")
    void 额外的登记项不算违规() {
        Set<String> missing = ResourceRegistryTestBase.findMissing(
                Set.of("datasource"),
                Set.of("datasource", "tunnel", "pipeline"));

        assertThat(missing).isEmpty();
    }
}
