package com.devnest.datasource.repository;

import com.devnest.datasource.entity.DataSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, Long> {
    boolean existsByName(String name);
    Optional<DataSourceConfig> findByName(String name);
}
