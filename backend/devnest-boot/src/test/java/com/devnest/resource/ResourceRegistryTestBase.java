package com.devnest.resource;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资源登记完整性的守护载体(为 P2 task 2.4 铺路,P0 阶段先建立载体本身)。
 *
 * <p><b>要防的问题(P2 起)</b>:DevNest 的多种资源(数据源、Redis 实例、隧道……)
 * 都要在统一的资源登记表里登记,才能被"统一回收/统一健康度/统一配额"治理。
 * 一旦新增一种资源却漏了登记,它就会游离在治理之外 —— 而且不会报错,
 * 只会在资源泄漏时才被发现。本类把"每种预期资源都必须被登记"变成可执行断言。
 *
 * <h3>P0 阶段的状态(诚实说明)</h3>
 * 当前 {@code core.resource} 的 {@code ResourceRegistry} 尚未建立,因此:
 * <ul>
 *   <li>{@link #actualResourceTypes()} 默认返回空集合 —— P2 落地时改为读取真实登记表;</li>
 *   <li>本类目前只有一个自检子类 {@link ResourceRegistryTestBaseSelfTest},
 *       用空预期"空跑",证明载体本身能跑通。</li>
 * </ul>
 *
 * <h3>为什么 {@link #expectedResourceTypes()} 是抽象方法而不是返回空集合的默认实现</h3>
 * 如果给默认实现,子类忘了重写就会返回空集合、测试永远通过 —— 守护机制自己变成
 * 了漏洞(路线图风险 R1「自指漏洞」)。声明为抽象方法后,每个子类都必须
 * <b>显式</b>写清"我期望哪些资源",漏写会编译不过,而不是静默通过。
 */
public abstract class ResourceRegistryTestBase {

    /**
     * 本测试类期望存在的资源类型标识。
     *
     * <p>刻意设计为抽象方法,强制子类显式声明(见类注释)。
     */
    protected abstract Set<String> expectedResourceTypes();

    /**
     * 实际已登记的资源类型标识。
     *
     * <p>P2 落地 {@code ResourceRegistry} 后,此处改为读取真实快照。
     */
    protected Set<String> actualResourceTypes() {
        return Set.of();
    }

    @Test
    void 所有预期资源类型都必须被登记() {
        Set<String> missing = findMissing(expectedResourceTypes(), actualResourceTypes());

        assertThat(missing)
                .as("%s 声明了以下预期资源类型,但资源登记表中没有它们;"
                        + "未登记的资源不会参与统一回收/健康度/配额治理",
                        getClass().getSimpleName())
                .isEmpty();
    }

    /**
     * 差集计算:预期中有、但实际未登记的类型。
     *
     * <p>抽成静态方法是为了让它可被直接测试 —— 守护机制的<b>判决逻辑本身</b>必须
     * 有测试,否则它到底有没有在判、判得对不对都无法回答(风险 R1)。
     *
     * @param expected 预期存在的资源类型
     * @param actual   实际已登记的资源类型
     * @return 缺失的类(按 {@code expected} 的迭代顺序)
     */
    static Set<String> findMissing(Set<String> expected, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }
}
