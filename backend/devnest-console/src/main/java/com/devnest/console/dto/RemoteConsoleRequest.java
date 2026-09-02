package com.devnest.console.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 远程控制台创建/更新请求.密码为明文,Service 层加密后入库.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
@Data
public class RemoteConsoleRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    /** 绑定的跳板ID,NULL=直连模式 */
    private Long bastionId;

    @NotBlank(message = "目标主机不能为空")
    private String remoteHost;

    private Integer remotePort = 22;

    @NotBlank(message = "SSH用户名不能为空")
    private String sshUser;

    /** 创建必填,更新可空(空=不改密码,Service 层校验) */
    private String sshPassword;

    private String remark;

    /** 快捷命令 JSON 字符串,可空;Service 层原样入库 */
    private String quickCommands;
}
