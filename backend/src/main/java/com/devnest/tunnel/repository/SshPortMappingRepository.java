package com.devnest.tunnel.repository;

import com.devnest.tunnel.entity.SshPortMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 端口映射模板 Repository.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public interface SshPortMappingRepository extends JpaRepository<SshPortMapping, Long> {

    List<SshPortMapping> findByBastionId(Long bastionId);

    void deleteByBastionId(Long bastionId);
}
