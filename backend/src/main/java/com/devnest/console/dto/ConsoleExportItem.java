package com.devnest.console.dto;

/**
 * 控制台导出项.含真实密码(导出时解密),用于跨环境迁移配置.
 * bastionName 用于导入时按名称查回跳板ID(跨环境ID不同,以名称为准).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 19:30
 */
public record ConsoleExportItem(
        String name,
        String bastionName,
        String remoteHost,
        Integer remotePort,
        String sshUser,
        String sshPassword,
        String remark,
        String quickCommands
) {
}
