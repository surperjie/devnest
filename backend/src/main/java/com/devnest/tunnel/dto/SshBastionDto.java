package com.devnest.tunnel.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SSH 跳板查询返回 DTO.密码字段脱敏,不含密文.
 * mappings 含映射详情(列表展开用),allocatedLocalPort 运行时填充.
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
        List<SshPortMappingDto> mappings,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
