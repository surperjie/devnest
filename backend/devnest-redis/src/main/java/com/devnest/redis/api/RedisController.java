package com.devnest.redis.api;

import com.devnest.common.response.ApiResult;
import com.devnest.redis.dto.RedisExecResultDto;
import com.devnest.redis.dto.RedisInfoDto;
import com.devnest.redis.dto.RedisInstanceConfigDto;
import com.devnest.redis.dto.RedisInstanceConfigRequest;
import com.devnest.redis.dto.RedisKeyListDto;
import com.devnest.redis.dto.RedisValueDto;
import com.devnest.redis.service.RedisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Redis 实例管理 + 可视化操作 REST API.
 *
 * CRUD 路径与 DataSourceController 风格保持一致;
 * ops 路径提供实例级实时操作(SIC 键扫描 / INFO / 命令执行).
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 14:30
 */
@RestController
@RequestMapping("/api/redis")
@RequiredArgsConstructor
public class RedisController {

    private final RedisService redisService;

    // ==================================================================
    // 实例 CRUD
    // ==================================================================

    @GetMapping
    public ApiResult<List<RedisInstanceConfigDto>> list() {
        return ApiResult.ok(redisService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResult<RedisInstanceConfigDto> get(@PathVariable Long id) {
        return ApiResult.ok(redisService.getById(id));
    }

    @PostMapping
    public ApiResult<RedisInstanceConfigDto> create(@Valid @RequestBody RedisInstanceConfigRequest req) {
        return ApiResult.ok(redisService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResult<RedisInstanceConfigDto> update(@PathVariable Long id,
                                                    @Valid @RequestBody RedisInstanceConfigRequest req) {
        return ApiResult.ok(redisService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        redisService.delete(id);
        return ApiResult.ok(null);
    }

    /** 测试已保存实例 */
    @PostMapping("/{id}/test")
    public ApiResult<Boolean> testConnection(@PathVariable Long id) {
        return ApiResult.ok(redisService.testConnection(id));
    }

    /** 测试未保存实例 */
    @PostMapping("/test")
    public ApiResult<Boolean> testConnectionDirect(@RequestBody RedisInstanceConfigRequest req) {
        return ApiResult.ok(redisService.testConnectionDirect(req));
    }

    // ==================================================================
    // 可视化操作 ops
    // ==================================================================

    /** INFO 概览 */
    @GetMapping("/{id}/ops/info")
    public ApiResult<RedisInfoDto> info(@PathVariable Long id) {
        return ApiResult.ok(redisService.info(id));
    }

    /** db 列表 */
    @GetMapping("/{id}/ops/dbs")
    public ApiResult<List<String>> listDbs(@PathVariable Long id) {
        return ApiResult.ok(redisService.listDbs(id));
    }

    /** SCAN 扫描 key */
    @GetMapping("/{id}/ops/keys")
    public ApiResult<RedisKeyListDto> scanKeys(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") String db,
            @RequestParam(required = false, defaultValue = "0") String cursor,
            @RequestParam(required = false) String pattern,
            @RequestParam(required = false) Integer count) {
        return ApiResult.ok(redisService.scanKeys(id, db, cursor, pattern, count));
    }

    /** 获取 key 的完整 value */
    @GetMapping("/{id}/ops/key")
    public ApiResult<RedisValueDto> getValue(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") String db,
            @RequestParam String key) {
        return ApiResult.ok(redisService.getValue(id, db, key));
    }

    /** 执行命令 */
    @PostMapping("/{id}/ops/exec")
    public ApiResult<RedisExecResultDto> execute(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") String db,
            @RequestBody String commandLine) {
        return ApiResult.ok(redisService.execute(id, db, commandLine));
    }

    /** 删除 key */
    @DeleteMapping("/{id}/ops/key")
    public ApiResult<RedisExecResultDto> delKey(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") String db,
            @RequestParam String key) {
        return ApiResult.ok(redisService.delKey(id, db, key));
    }
}
