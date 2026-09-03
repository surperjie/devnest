package com.devnest.tunnel.repository;

import com.devnest.tunnel.entity.SshPortMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 端口映射模板 Repository.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public interface SshPortMappingRepository extends JpaRepository<SshPortMapping, Long> {

    List<SshPortMapping> findByBastionId(Long bastionId);

    /** derived deleteBy* 声明 @Transactional, 避免 InvalidDataAccessApiUsageException. */
    @Modifying
    @Transactional
    void deleteByBastionId(Long bastionId);

    /** 查询所有 last_running=true 的端口映射所属的 bastionId(去重). */
    @Query("SELECT DISTINCT m.bastionId FROM SshPortMapping m WHERE m.lastRunning = true")
    List<Long> findDistinctBastionIdsByLastRunningTrue();

    /**
     * 批量把指定 bastionId 下所有 mapping 置位 lastRunning.
     * Repository 层直接声明 @Transactional,避免上层 Bean 自调用导致 AOP 代理失效
     * (Spring Data JPA Repository 本身就是 CGLIB 代理,@Transactional 100% 生效).
     */
    @Modifying
    @Transactional
    @Query("UPDATE SshPortMapping m SET m.lastRunning = :flag WHERE m.bastionId = :bastionId")
    int updateLastRunningByBastionId(@Param("bastionId") Long bastionId, @Param("flag") Boolean flag);
}
