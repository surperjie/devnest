package com.devnest.tunnel.mapper;

import com.devnest.tunnel.dto.SshBastionDto;
import com.devnest.tunnel.entity.SshBastion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * SshBastion ↔ DTO 映射.
 * sshPasswordMask / running / mappingCount 为业务字段,Service 填充.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@Mapper(componentModel = "spring")
public interface SshBastionMapper {

    @Mapping(target = "sshPasswordMask", ignore = true)
    @Mapping(target = "running", ignore = true)
    @Mapping(target = "mappingCount", ignore = true)
    SshBastionDto toDto(SshBastion entity);

    List<SshBastionDto> toDtoList(List<SshBastion> entities);
}
