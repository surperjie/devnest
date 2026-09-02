package com.devnest.datasource.mapper;

import com.devnest.datasource.dto.DataSourceDto;
import com.devnest.datasource.dto.SqlLogDto;
import com.devnest.datasource.entity.DataSourceConfig;
import com.devnest.datasource.entity.SqlExecutionLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@Mapper(componentModel = "spring")
public interface DataSourceMapper {

    DataSourceMapper INSTANCE = Mappers.getMapper(DataSourceMapper.class);

    DataSourceDto toDto(DataSourceConfig entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "datasourceName", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "errorMsg", ignore = true)
    @Mapping(target = "costMs", ignore = true)
    @Mapping(target = "rowCount", ignore = true)
    SqlLogDto toLogDto(SqlExecutionLog entity);
}
