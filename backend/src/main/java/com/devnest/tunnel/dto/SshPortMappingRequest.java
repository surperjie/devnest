package com.devnest.tunnel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 端口映射请求.preferredLocalPort 可空(自动分配).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public record SshPortMappingRequest(
        @NotBlank String remoteHost,
        @NotNull @Min(1) @Max(65535) Integer remotePort,
        @Min(1) @Max(65535) Integer preferredLocalPort,
        String label
) {
}
