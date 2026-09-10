package com.devnest.datasource.service.impl;

import com.devnest.common.context.CurrentUserProvider;
import com.devnest.common.crypto.CryptoService;
import com.devnest.core.pool.HikariPoolFactory;
import com.devnest.core.spi.TunnelPortForwarder;
import com.devnest.datasource.mapper.DataSourceMapper;
import com.devnest.datasource.repository.DataSourceConfigRepository;
import com.devnest.datasource.repository.SqlExecutionLogRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 连接上下文回收行为测试.
 * <p>
 * 守护点:配置变更后必须同时回收"连接池"和"隧道端口".
 * 此前只关了池、从不释放隧道端口,隧道模式下每次改配置都会漏掉一个本地转发端口.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/10 11:40
 */
class DatabaseQueryServiceImplEvictTest {

    private static final Long DS_ID = 1L;
    private static final int TUNNEL_PORT = 45678;

    private HikariPoolFactory poolFactory;
    private TunnelPortForwarder portForwarder;
    private DatabaseQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        DataSourceConfigRepository repo = mock(DataSourceConfigRepository.class);
        SqlExecutionLogRepository logRepo = mock(SqlExecutionLogRepository.class);
        DataSourceMapper mapper = mock(DataSourceMapper.class);
        CryptoService crypto = mock(CryptoService.class);
        poolFactory = mock(HikariPoolFactory.class);
        portForwarder = mock(TunnelPortForwarder.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);

        service = new DatabaseQueryServiceImpl(repo, logRepo, mapper, crypto, poolFactory,
                portForwarder, currentUserProvider);
    }

    @Test
    @DisplayName("evict:缓存立即失效,旧池与隧道端口延迟回收,不打断进行中的查询")
    void evictInvalidatesImmediatelyAndReleasesLater() throws Exception {
        cacheContextWithTunnelPort();

        service.evict(DS_ID);

        // 缓存立即失效:下一次查询会按最新配置重建连接
        Map<?, ?> connMap = (Map<?, ?>) ReflectionTestUtils.getField(service, "connMap");
        assertNotNull(connMap);
        assertTrue(connMap.isEmpty(), "缓存应立即失效,否则查询会继续用旧连接");
        // 但此刻还没有断连,已经在跑的查询可以正常收尾
        verify(poolFactory, never()).destroyPool(anyString());
        verify(portForwarder, never()).releaseTunnel(anyInt());

        // 延迟窗口过后:连接池与隧道端口一并回收
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(poolFactory).destroyPool("ds:" + DS_ID);
            verify(portForwarder).releaseTunnel(TUNNEL_PORT);
        });
    }

    @Test
    @DisplayName("evict:没有缓存时不报错,也不做任何释放")
    void evictOnMissingContextIsNoop() {
        service.evict(DS_ID);

        verify(poolFactory, never()).destroyPool(anyString());
        verify(portForwarder, never()).releaseTunnel(anyInt());
    }

    // ------------------------------------------------------------------

    /** 通过反射往私有缓存里放一个"隧道模式"的连接上下文 */
    @SuppressWarnings("unchecked")
    private void cacheContextWithTunnelPort() throws Exception {
        Class<?> ctxType = Class.forName(
                "com.devnest.datasource.service.impl.DatabaseQueryServiceImpl$ConnContext");
        Constructor<?> ctor = ctxType.getDeclaredConstructor(HikariDataSource.class, Integer.class);
        ctor.setAccessible(true);
        Object ctx = ctor.newInstance(mock(HikariDataSource.class), TUNNEL_PORT);

        Map<Long, Object> connMap =
                (Map<Long, Object>) ReflectionTestUtils.getField(service, "connMap");
        assertNotNull(connMap);
        connMap.put(DS_ID, ctx);
    }
}
