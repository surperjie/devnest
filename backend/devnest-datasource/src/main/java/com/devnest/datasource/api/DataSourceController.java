package com.devnest.datasource.api;

import com.devnest.common.response.ApiResult;
import com.devnest.datasource.dto.DataSourceDto;
import com.devnest.datasource.dto.DataSourceRequest;
import com.devnest.datasource.service.DataSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源管理 API.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@RestController
@RequestMapping("/api/datasource")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService dataSourceService;

    @GetMapping
    public ApiResult<List<DataSourceDto>> list() {
        return ApiResult.ok(dataSourceService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResult<DataSourceDto> get(@PathVariable Long id) {
        return ApiResult.ok(dataSourceService.getById(id));
    }

    @PostMapping
    public ApiResult<DataSourceDto> create(@Valid @RequestBody DataSourceRequest req) {
        return ApiResult.ok(dataSourceService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResult<DataSourceDto> update(@PathVariable Long id,
                                           @Valid @RequestBody DataSourceRequest req) {
        return ApiResult.ok(dataSourceService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return ApiResult.ok(null);
    }

    /** 测试已保存的数据源连接 */
    @PostMapping("/{id}/test")
    public ApiResult<Boolean> testConnection(@PathVariable Long id) {
        return ApiResult.ok(dataSourceService.testConnection(id));
    }

    /** 测试未保存的数据源(前端表单填写时) */
    @PostMapping("/test")
    public ApiResult<Boolean> testConnectionDirect(@RequestBody DataSourceRequest req) {
        return ApiResult.ok(dataSourceService.testConnectionDirect(req));
    }
}
