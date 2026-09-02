package com.devnest.console.api;

import com.devnest.common.response.ApiResult;
import com.devnest.console.dto.ConsoleExportPayload;
import com.devnest.console.dto.ConsoleImportResult;
import com.devnest.console.dto.RemoteConsoleDto;
import com.devnest.console.dto.RemoteConsoleRequest;
import com.devnest.console.service.RemoteConsoleService;
import com.devnest.console.ws.WsTokenManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 远程控制台会话 CRUD 接口.
 * 终端交互通过 WebSocket(/ws/console/{id}),不在此 Controller.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
@RestController
@RequestMapping("/api/console")
@RequiredArgsConstructor
public class RemoteConsoleController {

    private final RemoteConsoleService remoteConsoleService;

    @GetMapping("/consoles")
    public ApiResult<List<RemoteConsoleDto>> list() {
        return ApiResult.ok(remoteConsoleService.listConsoles());
    }

    @GetMapping("/consoles/{id}")
    public ApiResult<RemoteConsoleDto> get(@PathVariable Long id) {
        return ApiResult.ok(remoteConsoleService.getConsole(id));
    }

    @PostMapping("/consoles")
    public ApiResult<RemoteConsoleDto> create(@Valid @RequestBody RemoteConsoleRequest request) {
        return ApiResult.ok(remoteConsoleService.createConsole(request));
    }

    @PutMapping("/consoles/{id}")
    public ApiResult<RemoteConsoleDto> update(@PathVariable Long id,
                                              @Valid @RequestBody RemoteConsoleRequest request) {
        return ApiResult.ok(remoteConsoleService.updateConsole(id, request));
    }

    @DeleteMapping("/consoles/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        remoteConsoleService.deleteConsole(id);
        return ApiResult.ok();
    }

    @GetMapping("/consoles/export")
    public ApiResult<ConsoleExportPayload> exportConsoles() {
        return ApiResult.ok(remoteConsoleService.exportConsoles());
    }

    @PostMapping("/consoles/import")
    public ApiResult<ConsoleImportResult> importConsoles(@RequestBody ConsoleExportPayload payload) {
        return ApiResult.ok(remoteConsoleService.importConsoles(payload));
    }

    /**
     * 申请 WebSocket 握手 Token.
     * 前端先调此接口拿到一次性 token,再用 /ws/console/{id}?token=xxx 建立连接.
     * 这样任何非 HTTP 链路(例如未登录跨页面脚本)无法直接打开终端.
     */
    @PostMapping("/consoles/{id}/ws-token")
    public ApiResult<String> issueWsToken(@PathVariable Long id) {
        // 先确认存在,避免给无效 ID 发 token
        remoteConsoleService.getConsole(id);
        return ApiResult.ok(WsTokenManager.issue(id));
    }
}
