package com.devnest.console.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 远程控制台返回 DTO.密码字段脱敏(********),不返回明文/密文.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
@Data
public class RemoteConsoleDto {

    private Long id;
    private String name;
    private Long bastionId;
    private String remoteHost;
    private Integer remotePort;
    private String sshUser;
    /** 脱敏占位,已设密码返回 "********",未设返回 null */
    private String sshPasswordMasked;
    private String remark;
    /** 快捷命令 JSON 数组字符串,前端解析后渲染为按钮 */
    private String quickCommands;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
