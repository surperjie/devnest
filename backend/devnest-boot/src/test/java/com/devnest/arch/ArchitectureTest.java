package com.devnest.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * 架构守护骨架(P0 task 0.5)。
 *
 * <p><b>为什么需要它</b>:模块边界此前只写在文档里。{@code maven-enforcer-plugin} 的
 * {@code bannedDependencies} 守的是 <i>Maven 依赖声明</i>(pom 层面),而代码层面
 * 仍可以用"传递依赖 + 直接 import"绕过 —— enforcer 看不出 {@code console} 里
 * {@code import com.devnest.tunnel.some.impl.Thing} 这种真实的类级耦合。
 * 本类补上这一层:断言的是 <b>字节码里的实际引用</b>。两者是互补关系,不是重复。
 *
 * <p><b>首批只放 3 条规则</b>(对应路线图 C4 / C10 / C13),选型原则是
 * "存量已合规" —— 引入当天必须全绿,否则守卫会立刻被绕过并失去权威(铁律 2)。
 * 后续阶段(P1–P4)在此逐个追加,不修改已通过的规则。
 *
 * <p><b>规则编号与路线图的对应</b>:见 {@code docs/architecture/20260910_目标架构_v2.0.md}。
 *
 * @see <a href="https://www.archunit.org/userguide/html/000_Index.html">ArchUnit User Guide</a>
 */
@AnalyzeClasses(
        packages = "com.devnest",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    /**
     * boot 模块的类直接位于根包 {@code com.devnest} 下(无子包),
     * 因此必须用"精确包名"匹配;{@code com.devnest..} 会把所有模块一网打尽。
     */
    private static final String BOOT_PACKAGE = "com.devnest";

    /** 五个业务模块。它们之间禁止横向依赖,交互一律走 {@code core.spi}。 */
    private static final List<String> BUSINESS_MODULES =
            List.of("tunnel", "console", "datasource", "redis", "pipeline");

    /**
     * 形态判断的唯一合法落点(P1 建立)。业务代码不得直接写裸 {@code @ConditionalOnProperty},
     * 只能用 {@code @LocalMode} / {@code @ServerMode} 元注解 —— 否则形态语义会散落到各处,
     * 变成无法维护的形态判断。
     */
    private static final String MODE_PACKAGE = "com.devnest.core.mode";

    // ------------------------------------------------------------------
    // C13 的自定义判决条件。声明必须在本文件所有 @ArchTest 字段【之前】:
    // Java 规定静态字段初始化不能前向引用同文件中后声明的静态字段。
    // ------------------------------------------------------------------

    private static final ArchCondition<JavaClass> DEPRECATED_CLASS_MUST_DECLARE_SINCE =
            new ArchCondition<>("@Deprecated 必须声明非空 since 属性") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    item.tryGetAnnotationOfType(Deprecated.class)
                            .filter(deprecated -> isBlank(deprecated.since()))
                            .ifPresent(deprecated -> events.add(SimpleConditionEvent.violated(
                                    item,
                                    item.getName() + " 的 @Deprecated 缺少 since 属性")));
                }
            };

    private static final ArchCondition<JavaMethod> DEPRECATED_METHOD_MUST_DECLARE_SINCE =
            new ArchCondition<>("@Deprecated 必须声明非空 since 属性") {
                @Override
                public void check(JavaMethod item, ConditionEvents events) {
                    item.tryGetAnnotationOfType(Deprecated.class)
                            .filter(deprecated -> isBlank(deprecated.since()))
                            .ifPresent(deprecated -> events.add(SimpleConditionEvent.violated(
                                    item,
                                    item.getFullName() + " 的 @Deprecated 缺少 since 属性")));
                }
            };

    // ==================================================================
    // C4 · 分层方向:common ← core ← {tunnel, console, datasource, redis, pipeline} ← boot
    // ==================================================================

    @ArchTest
    static final ArchRule C4_1_common_不依赖任何其它内部模块 = noClasses()
            .that().resideInAPackage("com.devnest.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.devnest.core..",
                    "com.devnest.tunnel..",
                    "com.devnest.console..",
                    "com.devnest.datasource..",
                    "com.devnest.redis..",
                    "com.devnest.pipeline..",
                    BOOT_PACKAGE)
            .because("common 是依赖方向的最底层,可被任何模块依赖,但不得反向依赖任何模块");

    @ArchTest
    static final ArchRule C4_2_core_不依赖业务模块 = noClasses()
            .that().resideInAPackage("com.devnest.core..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.devnest.tunnel..",
                    "com.devnest.console..",
                    "com.devnest.datasource..",
                    "com.devnest.redis..",
                    "com.devnest.pipeline..",
                    BOOT_PACKAGE)
            .because("core 定义 SPI,业务模块实现 SPI;反向依赖会让 SPI 被具体实现绑死,"
                    + "从而失去'可替换'能力");

    /**
     * 业务模块两两之间禁止横向依赖。
     *
     * <p>写成循环而非 20 条独立规则:规则集是"正交组合"而非"逐个特例",
     * 新增业务模块时只需往 {@link #BUSINESS_MODULES} 加一个名字。
     */
    @ArchTest
    static void C4_3_业务模块之间禁止横向依赖(JavaClasses classes) {
        for (String from : BUSINESS_MODULES) {
            for (String to : BUSINESS_MODULES) {
                if (from.equals(to)) {
                    continue;
                }
                noClasses()
                        .that().resideInAPackage("com.devnest." + from + "..")
                        .should().dependOnClassesThat()
                        .resideInAPackage("com.devnest." + to + "..")
                        .because("业务模块 " + from + " 不得直接依赖 " + to
                                + ";需要跨模块能力时走 core.spi 接口,由 boot 装配")
                        .check(classes);
            }
        }
    }

    // ==================================================================
    // C10 · 形态判断集中一处:禁止裸 @ConditionalOnProperty
    // ==================================================================

    @ArchTest
    static final ArchRule C10_1_类上不得出现裸的_ConditionalOnProperty = noClasses()
            .that().resideOutsideOfPackage(MODE_PACKAGE + "..")
            .should().beAnnotatedWith(ConditionalOnProperty.class)
            .because("形态判断必须集中在 " + MODE_PACKAGE + ",用元注解表达;"
                    + "散落的 @ConditionalOnProperty 会让'当前是什么形态'无法回答");

    @ArchTest
    static final ArchRule C10_2_方法上不得出现裸的_ConditionalOnProperty = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage(MODE_PACKAGE + "..")
            .should().beAnnotatedWith(ConditionalOnProperty.class)
            .because("同 C10_1:形态判断的合法落点只有 " + MODE_PACKAGE);

    // ==================================================================
    // C13 · 弃用必须可追溯:@Deprecated 必须带 since
    // ==================================================================

    /**
     * 刻意<b>不</b>加 {@code .that().areAnnotatedWith(Deprecated.class)} 选择器。
     *
     * <p>加了选择器之后,当全库还没有任何 {@code @Deprecated} 时规则会匹配到空集合,
     * 而 ArchUnit 默认把"匹配为空"判为失败({@code failOnEmptyShould}),只能靠
     * {@code allowEmptyShould(true)} 放行 —— 但那会把"选择器写错导致什么都没匹配到"
     * 一起放行,守卫就变成了摆设。
     *
     * <p>改为对<b>全部</b>类施加"蕴含式"判决:判决条件自身只在遇到 {@code @Deprecated}
     * 时才产生违规。规则因此永远不会匹配为空,也就永远不会静默失效。
     */
    @ArchTest
    static final ArchRule C13_1_被弃用的类必须声明_since = classes()
            .should(DEPRECATED_CLASS_MUST_DECLARE_SINCE)
            .because("没有 since 的 @Deprecated 无法判断'从哪个版本起不该再用',"
                    + "也就无法决定何时可以安全删除");

    /**
     * 同 {@link #C13_1_被弃用的类必须声明_since}:不用选择器,避免"空集合 = 静默失效"。
     */
    @ArchTest
    static final ArchRule C13_2_被弃用的方法必须声明_since = methods()
            .should(DEPRECATED_METHOD_MUST_DECLARE_SINCE)
            .because("同 C13_1");

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
