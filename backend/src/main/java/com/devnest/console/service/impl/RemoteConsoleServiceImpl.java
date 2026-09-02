package com.devnest.console.service.impl;

import com.devnest.common.crypto.CryptoService;
import com.devnest.console.dto.ConsoleExportItem;
import com.devnest.console.dto.ConsoleExportPayload;
import com.devnest.console.dto.ConsoleImportResult;
import com.devnest.console.dto.RemoteConsoleDto;
import com.devnest.console.dto.RemoteConsoleRequest;
import com.devnest.console.entity.RemoteConsole;
import com.devnest.console.mapper.RemoteConsoleMapper;
import com.devnest.console.repository.RemoteConsoleRepository;
import com.devnest.console.service.RemoteConsoleService;
import com.devnest.tunnel.repository.SshBastionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 远程控制台会话服务实现.
 * 密码:创建必填加密,更新空则保留旧密码.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
@Service
@RequiredArgsConstructor
public class RemoteConsoleServiceImpl implements RemoteConsoleService {

    private final RemoteConsoleRepository repository;
    private final RemoteConsoleMapper mapper;
    private final CryptoService crypto;
    private final SshBastionRepository bastionRepo;

    @Override
    public List<RemoteConsoleDto> listConsoles() {
        return repository.findAll().stream()
                .map(this::toDtoWithMask)
                .toList();
    }

    @Override
    public RemoteConsoleDto getConsole(Long id) {
        RemoteConsole entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("控制台不存在: " + id));
        return toDtoWithMask(entity);
    }

    @Override
    @Transactional
    public RemoteConsoleDto createConsole(RemoteConsoleRequest request) {
        if (request.getSshPassword() == null || request.getSshPassword().isEmpty()) {
            throw new IllegalArgumentException("SSH密码不能为空");
        }
        if (repository.existsByName(request.getName())) {
            throw new IllegalArgumentException("控制台名称已存在: " + request.getName());
        }
        RemoteConsole entity = mapper.toEntity(request);
        entity.setSshPasswordCipher(crypto.encrypt(request.getSshPassword()));
        repository.save(entity);
        return toDtoWithMask(entity);
    }

    @Override
    @Transactional
    public RemoteConsoleDto updateConsole(Long id, RemoteConsoleRequest request) {
        RemoteConsole entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("控制台不存在: " + id));
        // 名称改了要查重
        if (!request.getName().equals(entity.getName())
                && repository.existsByName(request.getName())) {
            throw new IllegalArgumentException("控制台名称已存在: " + request.getName());
        }
        mapper.updateEntity(request, entity);
        // 密码非空才更新(支持留空不改密码)
        if (request.getSshPassword() != null && !request.getSshPassword().isEmpty()) {
            entity.setSshPasswordCipher(crypto.encrypt(request.getSshPassword()));
        }
        repository.save(entity);
        return toDtoWithMask(entity);
    }

    @Override
    @Transactional
    public void deleteConsole(Long id) {
        repository.deleteById(id);
    }

    @Override
    public ConsoleExportPayload exportConsoles() {
        List<ConsoleExportItem> items = repository.findAll().stream()
                .map(e -> {
                    String bastionName = e.getBastionId() == null ? null
                            : bastionRepo.findById(e.getBastionId())
                                    .map(b -> b.getName()).orElse(null);
                    return new ConsoleExportItem(
                            e.getName(), bastionName,
                            e.getRemoteHost(), e.getRemotePort(),
                            e.getSshUser(), crypto.decrypt(e.getSshPasswordCipher()),
                            e.getRemark(), e.getQuickCommands()
                    );
                }).toList();
        return new ConsoleExportPayload(1, LocalDateTime.now(), items);
    }

    @Override
    @Transactional
    public ConsoleImportResult importConsoles(ConsoleExportPayload payload) {
        if (payload == null || payload.consoles() == null) {
            return new ConsoleImportResult(0, 0, List.of());
        }
        int success = 0;
        List<String> skipped = new ArrayList<>();
        for (ConsoleExportItem item : payload.consoles()) {
            if (repository.existsByName(item.name())) {
                skipped.add(item.name());
                continue;
            }
            RemoteConsoleRequest req = new RemoteConsoleRequest();
            req.setName(item.name());
            req.setRemoteHost(item.remoteHost());
            req.setRemotePort(item.remotePort() != null ? item.remotePort() : 22);
            req.setSshUser(item.sshUser());
            req.setSshPassword(item.sshPassword());
            req.setRemark(item.remark());
            req.setQuickCommands(item.quickCommands());
            if (item.bastionName() != null && !item.bastionName().isEmpty()) {
                bastionRepo.findByName(item.bastionName())
                        .ifPresent(b -> req.setBastionId(b.getId()));
                // 找不到对应跳板则按直连导入(bastionId=null)
            }
            createConsole(req);
            success++;
        }
        return new ConsoleImportResult(success, skipped.size(), skipped);
    }

    private RemoteConsoleDto toDtoWithMask(RemoteConsole entity) {
        RemoteConsoleDto dto = mapper.toDto(entity);
        if (entity.getSshPasswordCipher() != null) {
            dto.setSshPasswordMasked("********");
        }
        return dto;
    }
}
