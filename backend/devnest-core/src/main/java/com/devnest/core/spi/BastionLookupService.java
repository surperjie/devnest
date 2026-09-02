package com.devnest.core.spi;

import java.util.Optional;

/**
 * 跳板机配置查询 SPI:供非 tunnel 模块(console/datasource/redis 等)复用跳板元数据.
 * 不暴露 JPA Repository,避免业务模块耦合持久化实现.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 15:40
 */
public interface BastionLookupService {

    /** 按名称查跳板 ID,找不到返回 Optional.empty */
    Optional<Long> findBastionIdByName(String name);

    /** 按 ID 反查跳板名,导入导出时使用 */
    Optional<String> findBastionNameById(Long id);
}
