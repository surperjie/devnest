package com.devnest.datasource.service;

import com.devnest.datasource.dto.DataSourceDto;
import com.devnest.datasource.dto.DataSourceRequest;

import java.util.List;

/**
 * 数据源管理服务:CRUD + 连接测试.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
public interface DataSourceService {

    List<DataSourceDto> listAll();

    DataSourceDto getById(Long id);

    DataSourceDto create(DataSourceRequest req);

    DataSourceDto update(Long id, DataSourceRequest req);

    void delete(Long id);

    /** 测试连接(不持久化连接池),返回 true/false */
    boolean testConnection(Long id);

    /** 测试连接(前端表单填完未保存时),返回 true/false */
    boolean testConnectionDirect(DataSourceRequest req);
}
