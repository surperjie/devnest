package com.devnest.tunnel.service.impl;

import com.devnest.common.crypto.CryptoService;
import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.tunnel.dto.SshBastionDto;
import com.devnest.tunnel.dto.SshBastionRequest;
import com.devnest.tunnel.dto.SshPortMappingDto;
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

import java.util.List;
import java.util.Objects;

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
        return bastionRepo.findAll().stream().map(this::toDto).toList();
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
        int count = saveMappings(b.getId(), req.mappings());
        return toDto(b, count);
    }

    @Override
    @Transactional
    public SshBastionDto updateBastion(Long id, SshBastionRequest req) {
        SshBastion b = bastionRepo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.BASTION_NOT_FOUND));
        if (tunnelManager.getInstance(id) != null) {
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
        int count = saveMappings(id, req.mappings());
        return toDto(b, count);
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
        return new TunnelStatusDto(id, b.getName(), TunnelState.IDLE.name(), dtos);
    }

    private int saveMappings(Long bastionId, List<com.devnest.tunnel.dto.SshPortMappingRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (com.devnest.tunnel.dto.SshPortMappingRequest r : reqs) {
            SshPortMapping m = mappingMapper.toEntity(r);
            m.setBastionId(bastionId);
            mappingRepo.save(m);
            count++;
        }
        return count;
    }

    private SshBastionDto toDto(SshBastion b) {
        int count = mappingRepo.findByBastionId(b.getId()).size();
        return toDto(b, count);
    }

    private SshBastionDto toDto(SshBastion b, int mappingCount) {
        SshTunnelInstance inst = tunnelManager.getInstance(b.getId());
        boolean running = inst != null && inst.getState().isRunning();
        return new SshBastionDto(
                b.getId(), b.getName(), b.getSshHost(), b.getSshPort(),
                b.getSshUser(), crypto.mask(), b.getRemark(),
                running, mappingCount, b.getCreateTime(), b.getUpdateTime());
    }

    private TunnelStatusDto toStatusDto(SshTunnelInstance inst) {
        return new TunnelStatusDto(
                inst.getBastion().getId(),
                inst.getBastion().getName(),
                inst.getState().name(),
                mappingMapper.toDtoList(inst.getMappings()));
    }
}
