package com.devnest.tunnel.service.impl;

import com.devnest.common.crypto.CryptoService;
import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.tunnel.dto.BastionExportItem;
import com.devnest.tunnel.dto.BastionExportPayload;
import com.devnest.tunnel.dto.BastionImportResult;
import com.devnest.tunnel.dto.SshBastionDto;
import com.devnest.tunnel.dto.SshBastionRequest;
import com.devnest.tunnel.dto.SshPortMappingDto;
import com.devnest.tunnel.dto.SshPortMappingRequest;
import com.devnest.tunnel.dto.TunnelStatusDto;
import com.devnest.tunnel.entity.SshBastion;
import com.devnest.tunnel.entity.SshPortMapping;
import com.devnest.tunnel.mapper.SshPortMappingMapper;
import com.devnest.tunnel.model.TunnelState;
import com.devnest.tunnel.repository.SshBastionRepository;
import com.devnest.tunnel.repository.SshPortMappingRepository;
import com.devnest.tunnel.service.SshTunnelService;
import com.devnest.tunnel.tunnel.SshTunnelInstance;
import com.devnest.tunnel.tunnel.SshTunnelManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SSH 隧道服务实现.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@Service
public class SshTunnelServiceImpl implements SshTunnelService {

    private final SshBastionRepository bastionRepo;
    private final SshPortMappingRepository mappingRepo;
    private final SshTunnelManager tunnelManager;
    private final SshPortMappingMapper mappingMapper;
    private final CryptoService crypto;

    public SshTunnelServiceImpl(SshBastionRepository bastionRepo,
                                 SshPortMappingRepository mappingRepo,
                                 SshTunnelManager tunnelManager,
                                 SshPortMappingMapper mappingMapper,
                                 CryptoService crypto) {
        this.bastionRepo = bastionRepo;
        this.mappingRepo = mappingRepo;
        this.tunnelManager = tunnelManager;
        this.mappingMapper = mappingMapper;
        this.crypto = crypto;
    }

    @Override
    public List<SshBastionDto> listBastions() {
        List<SshBastion> bastions = bastionRepo.findAll();
        if (bastions.isEmpty()) {
            return List.of();
        }
        // 一次查所有 mappings,按 bastionId 分组,避免 N+1
        Map<Long, List<SshPortMapping>> grouped = mappingRepo.findAll().stream()
                .collect(Collectors.groupingBy(SshPortMapping::getBastionId));
        return bastions.stream()
                .map(b -> toDto(b, grouped.getOrDefault(b.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public SshBastionDto createBastion(SshBastionRequest req) {
        if (bastionRepo.existsByName(req.name())) {
            throw new BizException(ErrorCode.BASTION_NAME_DUPLICATED);
        }
        SshBastion b = new SshBastion();
        b.setName(req.name());
        b.setSshHost(req.sshHost());
        b.setSshPort(req.sshPort());
        b.setSshUser(req.sshUser());
        b.setSshPasswordCipher(crypto.encrypt(req.sshPassword()));
        b.setRemark(req.remark());
        bastionRepo.save(b);
        List<SshPortMapping> mappings = saveMappings(b.getId(), req.mappings());
        return toDto(b, mappings);
    }

    @Override
    @Transactional
    public SshBastionDto updateBastion(Long id, SshBastionRequest req) {
        SshBastion b = bastionRepo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.BASTION_NOT_FOUND));
        if (tunnelManager.isActive(id)) {
            throw new BizException(ErrorCode.TUNNEL_ALREADY_RUNNING, "请先停止隧道再编辑");
        }
        if (!Objects.equals(b.getName(), req.name()) && bastionRepo.existsByName(req.name())) {
            throw new BizException(ErrorCode.BASTION_NAME_DUPLICATED);
        }
        b.setName(req.name());
        b.setSshHost(req.sshHost());
        b.setSshPort(req.sshPort());
        b.setSshUser(req.sshUser());
        if (req.sshPassword() != null && !req.sshPassword().isEmpty()) {
            b.setSshPasswordCipher(crypto.encrypt(req.sshPassword()));
        }
        b.setRemark(req.remark());
        bastionRepo.save(b);
        mappingRepo.deleteByBastionId(id);
        List<SshPortMapping> mappings = saveMappings(id, req.mappings());
        return toDto(b, mappings);
    }

    @Override
    @Transactional
    public void deleteBastion(Long id) {
        if (!bastionRepo.existsById(id)) {
            throw new BizException(ErrorCode.BASTION_NOT_FOUND);
        }
        if (tunnelManager.getInstance(id) != null) {
            tunnelManager.stopTunnel(id);
        }
        mappingRepo.deleteByBastionId(id);
        bastionRepo.deleteById(id);
    }

    @Override
    public void startTunnel(Long id) {
        tunnelManager.startTunnel(id);
    }

    @Override
    public void stopTunnel(Long id) {
        tunnelManager.stopTunnel(id);
    }

    @Override
    public List<TunnelStatusDto> listRunningStatus() {
        return tunnelManager.getAllInstances().stream()
                .map(this::toStatusDto)
                .toList();
    }

    @Override
    public TunnelStatusDto getStatus(Long id) {
        SshTunnelInstance inst = tunnelManager.getInstance(id);
        if (inst != null) {
            return toStatusDto(inst);
        }
        SshBastion b = bastionRepo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.BASTION_NOT_FOUND));
        List<SshPortMapping> ms = mappingRepo.findByBastionId(id);
        List<SshPortMappingDto> dtos = ms.stream().map(m -> new SshPortMappingDto(
                m.getId(), m.getBastionId(), m.getRemoteHost(), m.getRemotePort(),
                m.getPreferredLocalPort(), null, m.getLabel(),
                m.getCreateTime(), m.getUpdateTime())).toList();
        return new TunnelStatusDto(id, b.getName(), TunnelState.IDLE.name(), dtos, "");
    }

    @Override
    public BastionExportPayload exportBastions() {
        List<SshBastion> bastions = bastionRepo.findAll();
        Map<Long, List<SshPortMapping>> grouped = mappingRepo.findAll().stream()
                .collect(Collectors.groupingBy(SshPortMapping::getBastionId));
        List<BastionExportItem> items = bastions.stream()
                .map(b -> {
                    List<SshPortMapping> ms = grouped.getOrDefault(b.getId(), List.of());
                    List<SshPortMappingRequest> mappingReqs = ms.stream().map(m ->
                            new SshPortMappingRequest(m.getRemoteHost(), m.getRemotePort(),
                                    m.getPreferredLocalPort(), m.getLabel())).toList();
                    return new BastionExportItem(
                            b.getName(), b.getSshHost(), b.getSshPort(), b.getSshUser(),
                            crypto.decrypt(b.getSshPasswordCipher()),
                            b.getRemark(), mappingReqs);
                }).toList();
        return new BastionExportPayload(1, LocalDateTime.now(), items);
    }

    @Override
    @Transactional
    public BastionImportResult importBastions(BastionExportPayload payload) {
        if (payload == null || payload.bastions() == null) {
            return new BastionImportResult(0, 0, List.of());
        }
        int success = 0;
        List<String> skipped = new ArrayList<>();
        List<String> needPassword = new ArrayList<>();
        for (BastionExportItem item : payload.bastions()) {
            if (item == null) {
                continue;
            }
            String name = item.name();
            if (name == null || name.isBlank()) {
                skipped.add("<未命名> · 配置缺少名称");
                continue;
            }
            if (bastionRepo.existsByName(name)) {
                skipped.add(name + " · 已存在同名配置(如需覆盖请先删除原配置)");
                continue;
            }
            if (item.sshHost() == null || item.sshHost().isBlank()
                    || item.sshUser() == null || item.sshUser().isBlank()
                    || item.sshPort() == null) {
                skipped.add(name + " · 主机/端口/用户名信息不完整");
                continue;
            }
            // 导出文件密码已脱敏:不把占位符当真实密码入库,改为导入后提示用户重新输入
            String password = item.sshPassword();
            boolean missingPassword = CryptoService.isPlaceholder(password);
            if (missingPassword) {
                needPassword.add(name);
                password = "";
            }
            try {
                SshBastionRequest req = new SshBastionRequest(
                        name, item.sshHost(), item.sshPort(), item.sshUser(),
                        password, item.remark(), item.mappings());
                createBastion(req);
                success++;
            } catch (Exception e) {
                skipped.add(name + " · " + safeReason(e));
            }
        }
        return new BastionImportResult(success, skipped.size(), skipped, needPassword);
    }

    private static String safeReason(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        return msg.length() > 80 ? msg.substring(0, 80) + "…" : msg;
    }

    private List<SshPortMapping> saveMappings(Long bastionId, List<SshPortMappingRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            return List.of();
        }
        List<SshPortMapping> saved = new ArrayList<>(reqs.size());
        for (SshPortMappingRequest r : reqs) {
            SshPortMapping m = mappingMapper.toEntity(r);
            m.setBastionId(bastionId);
            mappingRepo.save(m);
            saved.add(m);
        }
        return saved;
    }

    private SshBastionDto toDto(SshBastion b, List<SshPortMapping> mappings) {
        SshTunnelInstance inst = tunnelManager.getInstance(b.getId());
        boolean running = inst != null && inst.getState().isRunning();
        String state = inst != null ? inst.getState().name() : TunnelState.IDLE.name();
        String statusDetail = inst != null ? inst.getStatusDetail() : "";
        List<SshPortMappingDto> mappingDtos = mappings.stream().map(m -> new SshPortMappingDto(
                m.getId(), m.getBastionId(), m.getRemoteHost(), m.getRemotePort(),
                m.getPreferredLocalPort(), null, m.getLabel(),
                m.getCreateTime(), m.getUpdateTime())).toList();
        return new SshBastionDto(
                b.getId(), b.getName(), b.getSshHost(), b.getSshPort(),
                b.getSshUser(), crypto.mask(), b.getRemark(),
                running, mappings.size(), mappingDtos, b.getCreateTime(), b.getUpdateTime(),
                state, statusDetail);
    }

    private TunnelStatusDto toStatusDto(SshTunnelInstance inst) {
        return new TunnelStatusDto(
                inst.getBastion().getId(),
                inst.getBastion().getName(),
                inst.getState().name(),
                mappingMapper.toDtoList(inst.getMappings()),
                inst.getStatusDetail());
    }
}
