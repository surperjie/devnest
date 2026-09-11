package com.devnest.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基座(P0 task 0.6):提供与生产一致的 MySQL 8 与 Redis 容器。
 *
 * <p><b>要解决的问题</b>:此前用 H2 内存库跑测试,存在方言分裂 —— H2 上绿的仓储测试
 * 推不出 MySQL 上没问题(H2 没有 MySQL 的字符集/排序规则/JSON 类型/锁语义)。
 * 这里让仓储测试连真实 MySQL,把"测试绿"重新变成"生产大概率没问题"的有效证据。
 *
 * <h3>为什么用惰性单例,而不是 {@code @BeforeAll} 或静态初始化块</h3>
 * <ul>
 *   <li><b>不能用静态初始化块</b>:类一经加载就启动容器,那么"Docker 不可用"
 *       发生在判决条件({@link EnabledIfDockerAvailable})生效<b>之前</b>,
 *       会直接抛异常让构建变红,优雅跳过就失效了。</li>
 *   <li><b>不能只在 {@code @BeforeAll} 启动</b>:Spring 的
 *       {@code @DynamicPropertySource} 在 {@code BeforeAllCallback} 阶段、
 *       即用户 {@code @BeforeAll} <b>之前</b>求值,此时容器还没起来,
 *       访问 {@code getJdbcUrl()} 会抛"容器未启动"。</li>
 * </ul>
 * 惰性持有者同时满足两者:类加载不启动容器;谁先用到谁触发启动
 * (无论是 {@code @DynamicPropertySource} 还是测试方法体内)。JVM 类初始化
 * 语义天然保证线程安全,无需显式加锁。
 *
 * <h3>生命周期</h3>
 * 容器是 JVM 级单例,由 Testcontainers 的 Ryuk 回收容器在 JVM 退出时清理,
 * 因此<b>不要</b>写 {@code @AfterAll} 去 stop —— 那会让第一个跑完的测试类
 * 把后续测试类正在用的容器关掉。
 *
 * <h3>落点说明(迁移触发条件)</h3>
 * 本类暂放在 boot 的 test 源集:boot 是唯一能看见所有模块的模块,无需为此
 * 新增一个只为测试存在的模块。当<b>第一个非 boot 模块</b>出现仓储测试
 * (预计 P2/P4)时,再把本类连同 {@link EnabledIfDockerAvailable} 抽到独立的
 * test-jar 模块。现在抽是过早抽象 —— 还没有第二个消费者。
 *
 * @see EnabledIfDockerAvailable
 */
public abstract class AbstractContainerTest {

    protected static final String MYSQL_IMAGE = "mysql:8.0.36";
    protected static final String REDIS_IMAGE = "redis:7.4-alpine";
    protected static final int REDIS_PORT = 6379;

    protected static final String MYSQL_DATABASE = "devnest";
    protected static final String MYSQL_USERNAME = "devnest";
    protected static final String MYSQL_PASSWORD = "devnest";

    /** 惰性初始化的持有者:首次被访问时才创建并启动容器。 */
    private static final class Containers {

        static final MySQLContainer<?> MYSQL = startMySql();
        static final GenericContainer<?> REDIS = startRedis();

        private static MySQLContainer<?> startMySql() {
            MySQLContainer<?> container = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
                    .withDatabaseName(MYSQL_DATABASE)
                    .withUsername(MYSQL_USERNAME)
                    .withPassword(MYSQL_PASSWORD)
                    // 与生产一致:utf8mb4,否则中文与 emoji 的排序/截断行为会与 MySQL 默认值不符
                    .withCommand(
                            "--character-set-server=utf8mb4",
                            "--collation-server=utf8mb4_unicode_ci");
            container.start();
            return container;
        }

        private static GenericContainer<?> startRedis() {
            // Redis 没有独立的 testcontainers 模块(已核实 org.testcontainers:redis 不存在),
            // 用 core 的 GenericContainer + 官方镜像
            GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(REDIS_PORT)
                    .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
            container.start();
            return container;
        }
    }

    /**
     * 把容器连接信息注册为 Spring 属性,使 {@code @SpringBootTest} 子类
     * 自动连到容器而非本地/内嵌实例。
     */
    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractContainerTest::mysqlJdbcUrl);
        registry.add("spring.datasource.username", AbstractContainerTest::mysqlUsername);
        registry.add("spring.datasource.password", AbstractContainerTest::mysqlPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.data.redis.host", AbstractContainerTest::redisHost);
        registry.add("spring.data.redis.port", AbstractContainerTest::redisPort);
    }

    protected static String mysqlJdbcUrl() {
        return Containers.MYSQL.getJdbcUrl();
    }

    protected static String mysqlUsername() {
        return Containers.MYSQL.getUsername();
    }

    protected static String mysqlPassword() {
        return Containers.MYSQL.getPassword();
    }

    protected static String redisHost() {
        return Containers.REDIS.getHost();
    }

    protected static int redisPort() {
        return Containers.REDIS.getMappedPort(REDIS_PORT);
    }
}
