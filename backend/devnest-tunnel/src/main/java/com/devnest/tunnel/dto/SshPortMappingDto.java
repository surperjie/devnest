package com.devnest.tunnel.dto;

import java.time.LocalDateTime;

/**
 * 端口映射 DTO.allocatedLocalPort 运行时填充,停止为 null.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public record SshPortMappingDto(
        Long id,
        Long bastionId,
        String remoteHost,
        Integer remotePort,
        Integer preferredLocalPort,
        Integer allocatedLocalPort,
        String label,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
