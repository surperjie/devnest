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
        LocalDateTime updateTime,
        /** 运行状态 IDLE/CONNECTING/RECONNECTING/RUNNING/ERROR/CLOSED,未启动为 IDLE */
        String state,
        /** 状态详情:连接中/第几次重试/失败原因等,供前端实时感知 */
        String statusDetail
) {
}
