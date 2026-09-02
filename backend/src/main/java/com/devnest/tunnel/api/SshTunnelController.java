package com.devnest.tunnel.api;

import com.devnest.common.response.ApiResult;
import com.devnest.tunnel.dto.SshBastionDto;
import com.devnest.tunnel.dto.SshBastionRequest;
import com.devnest.tunnel.dto.TunnelStatusDto;
import com.devnest.tunnel.service.SshTunnelService;
import jakarta.validation.Valid;
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
 * SSH 隧道 REST 接口.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@RestController
@RequestMapping("/api/tunnel")
public class SshTunnelController {

    private final SshTunnelService service;

    public SshTunnelController(SshTunnelService service) {
        this.service = service;
    }

    @GetMapping("/bastions")
    public ApiResult<List<SshBastionDto>> listBastions() {
        return ApiResult.ok(service.listBastions());
    }

    @PostMapping("/bastions")
    public ApiResult<SshBastionDto> createBastion(@Valid @RequestBody SshBastionRequest req) {
        return ApiResult.ok(service.createBastion(req));
    }

    @PutMapping("/bastions/{id}")
    public ApiResult<SshBastionDto> updateBastion(@PathVariable Long id,
                                                  @Valid @RequestBody SshBastionRequest req) {
        return ApiResult.ok(service.updateBastion(id, req));
    }

    @DeleteMapping("/bastions/{id}")
    public ApiResult<Void> deleteBastion(@PathVariable Long id) {
        service.deleteBastion(id);
        return ApiResult.ok();
    }

    @PostMapping("/bastions/{id}/start")
    public ApiResult<Void> startTunnel(@PathVariable Long id) {
        service.startTunnel(id);
        return ApiResult.ok();
    }

    @PostMapping("/bastions/{id}/stop")
    public ApiResult<Void> stopTunnel(@PathVariable Long id) {
        service.stopTunnel(id);
        return ApiResult.ok();
    }

    @GetMapping("/status")
    public ApiResult<List<TunnelStatusDto>> listRunningStatus() {
        return ApiResult.ok(service.listRunningStatus());
    }

    @GetMapping("/bastions/{id}/status")
    public ApiResult<TunnelStatusDto> getStatus(@PathVariable Long id) {
        return ApiResult.ok(service.getStatus(id));
    }
}
