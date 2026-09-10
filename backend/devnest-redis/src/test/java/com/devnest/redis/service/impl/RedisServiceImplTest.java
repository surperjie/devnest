package com.devnest.redis.service.impl;

import com.devnest.common.crypto.CryptoService;
import com.devnest.redis.dto.RedisInstanceConfigRequest;
import com.devnest.redis.entity.RedisInstanceConfig;
import com.devnest.redis.pool.RedisPoolFactory;
import com.devnest.redis.repository.RedisInstanceConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 配置变更时"是否重建连接池"的触发条件测试.
 * <p>
 * 守护点:重建连接池会关掉池并释放隧道端口,正在执行的命令会被打断.
 * 所以只有连接目标或池参数真的变了才该重建 —— 改备注、改名字不该重建.
 * 此前 update() 是无条件 rebuildPool,这里把条件固定下来.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/10 12:05
 */
class RedisServiceImplTest {

    private static final Long INSTANCE_ID = 1L;

    private RedisInstanceConfigRepository repo;
    private RedisPoolFactory poolFactory;

    private RedisServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(RedisInstanceConfigRepository.class);
        poolFactory = mock(RedisPoolFactory.class);
        CryptoService crypto = mock(CryptoService.class);
        service = new RedisServiceImpl(repo, poolFactory, crypto);

        when(repo.save(any(RedisInstanceConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(crypto.encrypt(any())).thenReturn("cipher-new");
    }

    // ==================== 不该重建的场景 ====================

    @Test
    @DisplayName("只改备注:不重建连接池,不打断正在执行的命令")
    void keepsPoolWhenOnlyRemarkChanged() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));

        // 除 remark 外全部沿用原值
        service.update(INSTANCE_ID, request(c, c.getName(), c.getHost(), c.getPort(), null,
                c.getDbIndex(), c.getTimeoutMs(), c.getMaxConnections(), c.getSshBastionId(), "换个备注"));

        verify(poolFactory, never()).rebuildPool(anyLong());
    }

    @Test
    @DisplayName("只改名字:不重建连接池")
    void keepsPoolWhenOnlyNameChanged() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));
        when(repo.existsByName("缓存集群-改名")).thenReturn(false);

        service.update(INSTANCE_ID, request(c, "缓存集群-改名", c.getHost(), c.getPort(), null,
                c.getDbIndex(), c.getTimeoutMs(), c.getMaxConnections(), c.getSshBastionId(), c.getRemark()));

        verify(poolFactory, never()).rebuildPool(anyLong());
    }

    @Test
    @DisplayName("只改默认库:不重建连接池(取到连接后会显式 select)")
    void keepsPoolWhenOnlyDbIndexChanged() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));

        service.update(INSTANCE_ID, request(c, c.getName(), c.getHost(), c.getPort(), null,
                5, c.getTimeoutMs(), c.getMaxConnections(), c.getSshBastionId(), c.getRemark()));

        verify(poolFactory, never()).rebuildPool(anyLong());
    }

    @Test
    @DisplayName("密码留空:视为不修改,不重建连接池")
    void keepsPoolWhenPasswordOmitted() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));

        service.update(INSTANCE_ID, request(c, c.getName(), c.getHost(), c.getPort(), null,
                c.getDbIndex(), c.getTimeoutMs(), c.getMaxConnections(), c.getSshBastionId(), c.getRemark()));

        verify(poolFactory, never()).rebuildPool(anyLong());
    }

    // ==================== 必须重建的场景 ====================

    @Test
    @DisplayName("改 host:重建连接池,旧连接不再被复用")
    void rebuildsPoolWhenHostChanged() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));

        service.update(INSTANCE_ID, request(c, c.getName(), "10.0.0.9", c.getPort(), null,
                c.getDbIndex(), c.getTimeoutMs(), c.getMaxConnections(), c.getSshBastionId(), c.getRemark()));

        verify(poolFactory).rebuildPool(INSTANCE_ID);
    }

    @Test
    @DisplayName("改端口:重建连接池")
    void rebuildsPoolWhenPortChanged() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));

        service.update(INSTANCE_ID, request(c, c.getName(), c.getHost(), 6380, null,
                c.getDbIndex(), c.getTimeoutMs(), c.getMaxConnections(), c.getSshBastionId(), c.getRemark()));

        verify(poolFactory).rebuildPool(INSTANCE_ID);
    }

    @Test
    @DisplayName("改密码:重建连接池,旧凭据不再被复用")
    void rebuildsPoolWhenPasswordChanged() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));

        service.update(INSTANCE_ID, request(c, c.getName(), c.getHost(), c.getPort(), "brand-new-secret",
                c.getDbIndex(), c.getTimeoutMs(), c.getMaxConnections(), c.getSshBastionId(), c.getRemark()));

        verify(poolFactory).rebuildPool(INSTANCE_ID);
    }

    @Test
    @DisplayName("改连接超时:重建连接池(超时是池参数)")
    void rebuildsPoolWhenTimeoutChanged() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));

        service.update(INSTANCE_ID, request(c, c.getName(), c.getHost(), c.getPort(), null,
                c.getDbIndex(), 5000, c.getMaxConnections(), c.getSshBastionId(), c.getRemark()));

        verify(poolFactory).rebuildPool(INSTANCE_ID);
    }

    @Test
    @DisplayName("改最大连接数:重建连接池(池容量变了)")
    void rebuildsPoolWhenMaxConnectionsChanged() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));

        service.update(INSTANCE_ID, request(c, c.getName(), c.getHost(), c.getPort(), null,
                c.getDbIndex(), c.getTimeoutMs(), 32, c.getSshBastionId(), c.getRemark()));

        verify(poolFactory).rebuildPool(INSTANCE_ID);
    }

    @Test
    @DisplayName("改隧道绑定:重建连接池,顺带释放旧隧道端口")
    void rebuildsPoolWhenBastionChanged() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));

        service.update(INSTANCE_ID, request(c, c.getName(), c.getHost(), c.getPort(), null,
                c.getDbIndex(), c.getTimeoutMs(), c.getMaxConnections(), 9L, c.getRemark()));

        verify(poolFactory).rebuildPool(INSTANCE_ID);
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除实例:销毁连接池并释放隧道端口")
    void deleteDestroysPool() {
        RedisInstanceConfig c = existingConfig();
        when(repo.findById(INSTANCE_ID)).thenReturn(Optional.of(c));

        service.delete(INSTANCE_ID);

        verify(poolFactory).destroyPool(RedisPoolFactory.poolKey(INSTANCE_ID));
        verify(repo).delete(c);
    }

    // ------------------------------------------------------------------

    private static RedisInstanceConfig existingConfig() {
        RedisInstanceConfig c = new RedisInstanceConfig();
        c.setId(INSTANCE_ID);
        c.setName("缓存集群");
        c.setHost("10.0.0.1");
        c.setPort(6379);
        c.setPasswordCipher("cipher-old");
        c.setDbIndex(0);
        c.setTimeoutMs(2000);
        c.setMaxConnections(8);
        c.setSshBastionId(null);
        c.setRemark("旧备注");
        return c;
    }

    private static RedisInstanceConfigRequest request(RedisInstanceConfig c, String name, String host,
                                                      Integer port, String password, Integer dbIndex,
                                                      Integer timeoutMs, Integer maxConnections,
                                                      Long bastionId, String remark) {
        return new RedisInstanceConfigRequest(name, host, port, password, dbIndex, timeoutMs,
                maxConnections, bastionId, remark);
    }
}
