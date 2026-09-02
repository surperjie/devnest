package com.devnest.console.mapper;

import com.devnest.console.dto.RemoteConsoleDto;
import com.devnest.console.dto.RemoteConsoleRequest;
import com.devnest.console.entity.RemoteConsole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * 远程控制台 MapStruct 映射.
 * 密码字段不映射(Service 层手动加密 set),时间/id 不映射(数据库/基类维护).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 10:00
 */
@Mapper(componentModel = "spring")
public interface RemoteConsoleMapper {

    @Mapping(target = "sshPasswordMasked", ignore = true)
    RemoteConsoleDto toDto(RemoteConsole entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sshPasswordCipher", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    RemoteConsole toEntity(RemoteConsoleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sshPasswordCipher", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    void updateEntity(RemoteConsoleRequest request, @MappingTarget RemoteConsole entity);
}
