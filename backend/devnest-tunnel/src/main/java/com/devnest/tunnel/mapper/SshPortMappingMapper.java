package com.devnest.tunnel.mapper;

import com.devnest.tunnel.dto.SshPortMappingDto;
import com.devnest.tunnel.dto.SshPortMappingRequest;
import com.devnest.tunnel.entity.SshPortMapping;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * SshPortMapping ↔ DTO/Request 映射.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@Mapper(componentModel = "spring")
public interface SshPortMappingMapper {

    SshPortMappingDto toDto(SshPortMapping entity);

    List<SshPortMappingDto> toDtoList(List<SshPortMapping> entities);

    SshPortMapping toEntity(SshPortMappingRequest request);
}
