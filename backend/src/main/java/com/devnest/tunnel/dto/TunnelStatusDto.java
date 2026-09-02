package com.devnest.tunnel.dto;

import java.util.List;

/**
 * 隧道运行状态 DTO.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public record TunnelStatusDto(
        Long bastionId,
        String name,
        String state,
        List<SshPortMappingDto> mappings
) {
}
