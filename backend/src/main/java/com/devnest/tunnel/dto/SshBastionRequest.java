package com.devnest.tunnel.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * SSH 跳板新增/编辑请求.sshPassword 新增必填,编辑空表示不改.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public record SshBastionRequest(
        @NotBlank String name,
        @NotBlank String sshHost,
        @NotNull Integer sshPort,
        @NotBlank String sshUser,
        String sshPassword,
        String remark,
        @Valid List<SshPortMappingRequest> mappings
) {
}
