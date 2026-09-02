package com.devnest.tunnel.dto;

import java.util.List;

/**
 * 跳板导出项.含真实密码(导出时解密),用于跨环境迁移配置.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 16:40
 */
public record BastionExportItem(
        String name,
        String sshHost,
        Integer sshPort,
        String sshUser,
        String sshPassword,
        String remark,
        List<SshPortMappingRequest> mappings
) {
}
