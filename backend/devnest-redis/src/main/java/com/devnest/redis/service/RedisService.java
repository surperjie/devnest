package com.devnest.redis.service;

import com.devnest.redis.dto.RedisExecResultDto;
import com.devnest.redis.dto.RedisInfoDto;
import com.devnest.redis.dto.RedisInstanceConfigDto;
import com.devnest.redis.dto.RedisInstanceConfigRequest;
import com.devnest.redis.dto.RedisKeyListDto;
import com.devnest.redis.dto.RedisValueDto;

import java.util.List;

/**
 * Redis 实例管理 + 可视化操作服务.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 14:20
 */
public interface RedisService {

    List<RedisInstanceConfigDto> listAll();

    RedisInstanceConfigDto getById(Long id);

    RedisInstanceConfigDto create(RedisInstanceConfigRequest req);

    RedisInstanceConfigDto update(Long id, RedisInstanceConfigRequest req);

    void delete(Long id);

    /** 测试已保存实例连接 */
    boolean testConnection(Long id);

    /** 测试未保存实例(前端表单填写时) */
    boolean testConnectionDirect(RedisInstanceConfigRequest req);

    /** INFO 概览 */
    RedisInfoDto info(Long id);

    /** SCAN key 扫描 */
    RedisKeyListDto scanKeys(Long id, String db, String cursor, String pattern, Integer count);

    /** 按 TYPE 获取完整 value */
    RedisValueDto getValue(Long id, String db, String key);

    /** 获取当前实例可用的 db 数量 */
    List<String> listDbs(Long id);

    /** 执行白名单校验后的命令 */
    RedisExecResultDto execute(Long id, String db, String commandLine);

    /** 管理命令: DEL key */
    RedisExecResultDto delKey(Long id, String db, String key);
}
