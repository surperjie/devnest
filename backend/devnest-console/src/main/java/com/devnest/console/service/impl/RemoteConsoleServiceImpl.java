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
import com.devnest.core.spi.BastionLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 远程控制台会话服务实现.
 * 密码:创建必填加密,更新空则保留旧密码.
 * 跳板元数据通过 BastionLookupService SPI 获取,不再直接依赖 tunnel repository.
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
    private final BastionLookupService bastionLookup;

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
        if (!request.getName().equals(entity.getName())
                && repository.existsByName(request.getName())) {
            throw new IllegalArgumentException("控制台名称已存在: " + request.getName());
        }
        mapper.updateEntity(request, entity);
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
                .map(e -> new ConsoleExportItem(
                        e.getName(),
                        bastionLookup.findBastionNameById(e.getBastionId()).orElse(null),
                        e.getRemoteHost(), e.getRemotePort(),
                        e.getSshUser(), crypto.decrypt(e.getSshPasswordCipher()),
                        e.getRemark(), e.getQuickCommands()
                )).toList();
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
        List<String> needPassword = new ArrayList<>();
        for (ConsoleExportItem item : payload.consoles()) {
            if (item == null) {
                continue;
            }
            String name = item.name();
            if (name == null || name.isBlank()) {
                skipped.add("<未命名> · 配置缺少名称");
                continue;
            }
            if (repository.existsByName(name)) {
                skipped.add(name + " · 已存在同名配置(如需覆盖请先删除原配置)");
                continue;
            }
            if (item.remoteHost() == null || item.remoteHost().isBlank()
                    || item.sshUser() == null || item.sshUser().isBlank()) {
                skipped.add(name + " · 目标主机/用户名信息不完整");
                continue;
            }
            // 隧道模式控制台依赖目标机器上的同名跳板,缺失则无法使用,明确跳过并提示先导隧道
            String bastionName = item.bastionName();
            Long bastionId = null;
            if (bastionName != null && !bastionName.isBlank()) {
                bastionId = bastionLookup.findBastionIdByName(bastionName).orElse(null);
                if (bastionId == null) {
                    skipped.add(name + " · 依赖的跳板「" + bastionName + "」不存在,请先在 SSH 隧道页导入该跳板配置");
                    continue;
                }
            }
            // 导出文件密码已脱敏:不把占位符当真实密码入库,改为导入后提示用户重新输入
            String password = item.sshPassword();
            boolean missingPassword = CryptoService.isPlaceholder(password);
            if (missingPassword) {
                needPassword.add(name);
                password = "";
            }
            try {
                RemoteConsoleRequest req = new RemoteConsoleRequest();
                req.setName(name);
                req.setBastionId(bastionId);
                req.setRemoteHost(item.remoteHost());
                req.setRemotePort(item.remotePort() != null ? item.remotePort() : 22);
                req.setSshUser(item.sshUser());
                req.setRemark(item.remark());
                req.setQuickCommands(item.quickCommands());
                if (missingPassword) {
                    // 占位密码不写入:直接落库空密码密文(DB 非空约束),待用户编辑时重新输入
                    req.setSshPassword(password);
                    RemoteConsole entity = mapper.toEntity(req);
                    entity.setSshPasswordCipher(crypto.encrypt(password));
                    repository.save(entity);
                } else {
                    req.setSshPassword(password);
                    createConsole(req);
                }
                success++;
            } catch (Exception e) {
                skipped.add(name + " · " + safeReason(e));
            }
        }
        return new ConsoleImportResult(success, skipped.size(), skipped, needPassword);
    }

    private static String safeReason(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        return msg.length() > 80 ? msg.substring(0, 80) + "…" : msg;
    }

    private RemoteConsoleDto toDtoWithMask(RemoteConsole entity) {
        RemoteConsoleDto dto = mapper.toDto(entity);
        if (entity.getSshPasswordCipher() != null) {
            dto.setSshPasswordMasked("********");
        }
        return dto;
    }
}
