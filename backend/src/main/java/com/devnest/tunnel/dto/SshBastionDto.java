package com.devnest.tunnel.dto;

import java.time.LocalDateTime;

/**
 * SSH 跳板查询返回 DTO.密码字段脱敏,不含密文.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public record SshBastionDto(
        Long id,
        String name,
        String sshHost,
        Integer sshPort,
        String sshUser,
        String sshPasswordMask,
        String remark,
        Boolean running,
        Integer mappingCount,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
