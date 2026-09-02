package com.devnest.tunnel.repository;

import com.devnest.tunnel.entity.SshBastion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * SSH 跳板配置 Repository.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public interface SshBastionRepository extends JpaRepository<SshBastion, Long> {

    Optional<SshBastion> findByName(String name);

    boolean existsByName(String name);
}
