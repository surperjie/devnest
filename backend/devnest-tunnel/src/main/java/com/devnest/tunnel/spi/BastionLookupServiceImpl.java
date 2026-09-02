package com.devnest.tunnel.spi;

import com.devnest.core.spi.BastionLookupService;
import com.devnest.tunnel.entity.SshBastion;
import com.devnest.tunnel.repository.SshBastionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * BastionLookupService 默认实现 - 基于 SshBastionRepository 按名称/ID 查询.
 * 作为 Spring Bean 注册,非 tunnel 模块通过 core 依赖注入 SPI 接口即可使用.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 15:45
 */
@Service
public class BastionLookupServiceImpl implements BastionLookupService {

    private final SshBastionRepository bastionRepository;

    public BastionLookupServiceImpl(SshBastionRepository bastionRepository) {
        this.bastionRepository = bastionRepository;
    }

    @Override
    public Optional<Long> findBastionIdByName(String name) {
        if (name == null || name.isEmpty()) return Optional.empty();
        return bastionRepository.findByName(name).map(SshBastion::getId);
    }

    @Override
    public Optional<String> findBastionNameById(Long id) {
        if (id == null) return Optional.empty();
        return bastionRepository.findById(id).map(SshBastion::getName);
    }
}
