package com.devnest.core.pool;

import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连接池工厂行为测试:用 H2 内存库驱动真实的 Hikari,验证"销毁后按新配置重建"这一契约.
 * <p>
 * 这是配置变更能否真正生效的最后一环:上层(DataSourceService)只负责丢弃缓存,
 * "按新 URL 建新池"由本类保证 —— 这里跑真实连接池,不做 mock.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/10 11:50
 */
class HikariPoolFactoryTest {

    private static final String URL_A = "jdbc:h2:mem:devnest_pool_a;DB_CLOSE_DELAY=-1";
    private static final String URL_B = "jdbc:h2:mem:devnest_pool_b;DB_CLOSE_DELAY=-1";
    private static final String DRIVER = "org.h2.Driver";

    private HikariPoolFactory factory;

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.destroyAll();
        }
    }

    @Test
    @DisplayName("同一个 key 复用同一个池,不重复创建")
    void reusesExistingPool() {
        HikariPoolFactory f = factoryWithMaxPools(5);

        HikariDataSource first = f.getOrCreate("ds:1", URL_A, DRIVER, "sa", "");
        HikariDataSource second = f.getOrCreate("ds:1", URL_A, DRIVER, "sa", "");

        assertSame(first, second);
        assertEquals(1, f.activePoolCount());
    }

    @Test
    @DisplayName("销毁后按新配置重建:池是新的,URL 也换成了新的")
    void rebuildsWithNewConfigAfterDestroy() {
        HikariPoolFactory f = factoryWithMaxPools(5);
        HikariDataSource oldPool = f.getOrCreate("ds:1", URL_A, DRIVER, "sa", "");

        // 模拟配置变更:丢弃旧池
        f.destroyPool("ds:1");
        assertEquals(0, f.activePoolCount(), "销毁后计数应归零");

        HikariDataSource newPool = f.getOrCreate("ds:1", URL_B, DRIVER, "sa", "");

        assertNotSame(oldPool, newPool, "应创建新池,而不是复用已销毁的池");
        assertEquals(URL_B, newPool.getJdbcUrl(), "新池必须使用新配置的 URL");
        assertTrue(oldPool.isClosed(), "旧池应已关闭");
        assertEquals(1, f.activePoolCount());
    }

    @Test
    @DisplayName("池数量达到上限:拒绝创建并给出明确错误码")
    void rejectsWhenPoolLimitReached() {
        HikariPoolFactory f = factoryWithMaxPools(1);
        f.getOrCreate("ds:1", URL_A, DRIVER, "sa", "");

        BizException ex = assertThrows(BizException.class,
                () -> f.getOrCreate("ds:2", URL_B, DRIVER, "sa", ""));

        assertEquals(ErrorCode.DATASOURCE_POOL_LIMIT_EXCEEDED.code(), ex.getCode());
        assertEquals(1, f.activePoolCount(), "被拒绝的创建不应占用名额");
    }

    @Test
    @DisplayName("上限统计的是「同时存在」而非「累计创建」:销毁后可再建")
    void limitCountsLivePoolsNotHistory() {
        HikariPoolFactory f = factoryWithMaxPools(1);
        f.getOrCreate("ds:1", URL_A, DRIVER, "sa", "");
        f.destroyPool("ds:1");

        HikariDataSource pool = f.getOrCreate("ds:2", URL_B, DRIVER, "sa", "");

        assertEquals(URL_B, pool.getJdbcUrl());
        assertEquals(1, f.activePoolCount());
    }

    @Test
    @DisplayName("destroyAll 关闭全部池并清空计数")
    void destroyAllClosesEverything() {
        HikariPoolFactory f = factoryWithMaxPools(5);
        HikariDataSource a = f.getOrCreate("ds:1", URL_A, DRIVER, "sa", "");
        HikariDataSource b = f.getOrCreate("ds:2", URL_B, DRIVER, "sa", "");

        f.destroyAll();

        assertTrue(a.isClosed());
        assertTrue(b.isClosed());
        assertEquals(0, f.activePoolCount());
    }

    // ------------------------------------------------------------------

    private HikariPoolFactory factoryWithMaxPools(int maxPools) {
        PoolProperties props = new PoolProperties();
        props.setMaxPools(maxPools);
        props.setMaxPoolSize(2);
        props.setMinIdle(0);
        factory = new HikariPoolFactory(props);
        return factory;
    }
}
