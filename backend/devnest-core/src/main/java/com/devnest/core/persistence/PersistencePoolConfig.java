package com.devnest.core.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 本地配置库(H2/MySQL)连接池显式化.
 * <p>
 * Spring Boot 自动配置的默认值:maximumPoolSize=10,无 leakDetection.
 * 作为展示/高并发场景,显式声明一份 Hikari 参数,避免"默认值即合理"的展示瑕疵.
 * Flyway + JPA + Repositories 都使用这份主数据源.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
@Configuration
public class PersistencePoolConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(org.springframework.boot.autoconfigure.jdbc.DataSourceProperties props) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(props.determineDriverClassName())
                .url(props.determineUrl())
                .username(props.determineUsername())
                .password(props.determinePassword())
                .build();
        ds.setPoolName("devnest-config-pool");
        // 显式参数(可被 application.yml 的 spring.datasource.hikari.* 覆盖)
        ds.setMaximumPoolSize(Math.max(ds.getMaximumPoolSize(), 20));
        if (ds.getMinimumIdle() <= 0) ds.setMinimumIdle(5);
        if (ds.getConnectionTimeout() <= 0) ds.setConnectionTimeout(2000);
        if (ds.getLeakDetectionThreshold() <= 0) ds.setLeakDetectionThreshold(2000);
        if (ds.getIdleTimeout() <= 0) ds.setIdleTimeout(600_000);
        return ds;
    }
}
