package com.devnest.console.repository;

import com.devnest.console.entity.RemoteConsole;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 远程控制台会话仓储.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
public interface RemoteConsoleRepository extends JpaRepository<RemoteConsole, Long> {
    boolean existsByName(String name);
}
