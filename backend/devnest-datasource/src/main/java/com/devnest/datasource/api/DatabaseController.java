package com.devnest.datasource.api;

import com.devnest.common.response.ApiResult;
import com.devnest.datasource.dto.MultiSqlResult;
import com.devnest.datasource.dto.SchemaNode;
import com.devnest.datasource.dto.SqlLogDto;
import com.devnest.datasource.dto.TableDataResult;
import com.devnest.datasource.service.DatabaseQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据库查询 API:Schema 树 / 数据预览 / SQL 执行 / 日志.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 17:00
 */
@RestController
@RequestMapping("/api/datasource/{dsId}")
@RequiredArgsConstructor
public class DatabaseController {

    private final DatabaseQueryService queryService;

    /** 获取库表结构树 */
    @GetMapping("/schema")
    public ApiResult<List<SchemaNode>> getSchema(@PathVariable Long dsId) {
        return ApiResult.ok(queryService.getSchemaTree(dsId));
    }

    /** 分页预览表数据(可指定库名) */
    @GetMapping("/preview")
    public ApiResult<TableDataResult> preview(
            @PathVariable Long dsId,
            @RequestParam(required = false) String database,
            @RequestParam String table,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResult.ok(queryService.previewTable(dsId, database, table, page, size));
    }

    /** 执行 SQL(支持多语句,经黑名单校验,返回多个结果集) */
    @PostMapping("/sql")
    public ApiResult<MultiSqlResult> executeSql(
            @PathVariable Long dsId,
            @RequestBody Map<String, Object> body) {
        String sql = (String) body.get("sql");
        int maxRows = (int) body.getOrDefault("maxRows", 200);
        return ApiResult.ok(queryService.executeSql(dsId, sql, maxRows));
    }

    /** SQL 执行历史(分页) */
    @GetMapping("/sql-history")
    public ApiResult<Page<SqlLogDto>> history(
            @PathVariable Long dsId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResult.ok(queryService.getSqlHistory(dsId, pageable));
    }

    /** 最近 SQL(快捷复用,最多20条) */
    @GetMapping("/sql-recent")
    public ApiResult<List<SqlLogDto>> recent(@PathVariable Long dsId) {
        return ApiResult.ok(queryService.getRecentSql(dsId));
    }
}
