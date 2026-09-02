package com.devnest.console.service;

import com.devnest.console.dto.ConsoleExportPayload;
import com.devnest.console.dto.ConsoleImportResult;
import com.devnest.console.dto.RemoteConsoleDto;
import com.devnest.console.dto.RemoteConsoleRequest;

import java.util.List;

/**
 * 远程控制台会话服务.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
public interface RemoteConsoleService {

    List<RemoteConsoleDto> listConsoles();

    RemoteConsoleDto getConsole(Long id);

    RemoteConsoleDto createConsole(RemoteConsoleRequest request);

    RemoteConsoleDto updateConsole(Long id, RemoteConsoleRequest request);

    void deleteConsole(Long id);

    /** 导出全部控制台配置(密码解密,跳板按名称关联) */
    ConsoleExportPayload exportConsoles();

    /** 导入控制台配置(重名跳过,返回成功/跳过统计) */
    ConsoleImportResult importConsoles(ConsoleExportPayload payload);
}
