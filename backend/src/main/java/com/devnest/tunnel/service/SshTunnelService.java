package com.devnest.tunnel.service;

import com.devnest.tunnel.dto.BastionExportPayload;
import com.devnest.tunnel.dto.BastionImportResult;
import com.devnest.tunnel.dto.SshBastionDto;
import com.devnest.tunnel.dto.SshBastionRequest;
import com.devnest.tunnel.dto.TunnelStatusDto;

import java.util.List;

/**
 * SSH 隧道业务服务:跳板 CRUD + 隧道启停 + 状态查询.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public interface SshTunnelService {

    List<SshBastionDto> listBastions();

    SshBastionDto createBastion(SshBastionRequest request);

    SshBastionDto updateBastion(Long id, SshBastionRequest request);

    void deleteBastion(Long id);

    void startTunnel(Long id);

    void stopTunnel(Long id);

    /** 所有运行中隧道状态 */
    List<TunnelStatusDto> listRunningStatus();

    /** 单个隧道状态(含 mappings,未运行返回 IDLE + 库中 mappings) */
    TunnelStatusDto getStatus(Long id);

    /** 导出所有跳板配置(含真实密码 + 映射) */
    BastionExportPayload exportBastions();

    /** 导入跳板配置(重名跳过,返回成功/跳过统计) */
    BastionImportResult importBastions(BastionExportPayload payload);
}
