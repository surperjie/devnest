package com.devnest.redis.service.impl;

import com.devnest.common.crypto.CryptoService;
import com.devnest.common.exception.BizException;
import com.devnest.common.exception.ErrorCode;
import com.devnest.redis.dto.RedisExecResultDto;
import com.devnest.redis.dto.RedisInfoDto;
import com.devnest.redis.dto.RedisInstanceConfigDto;
import com.devnest.redis.dto.RedisInstanceConfigRequest;
import com.devnest.redis.dto.RedisKeyListDto;
import com.devnest.redis.dto.RedisValueDto;
import com.devnest.redis.entity.RedisInstanceConfig;
import com.devnest.redis.pool.RedisPoolFactory;
import com.devnest.redis.repository.RedisInstanceConfigRepository;
import com.devnest.redis.security.RedisCommandWhitelist;
import com.devnest.redis.security.RedisCommandWhitelist.ValidationResult;
import com.devnest.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis 可视化服务实现.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 14:20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {

    private final RedisInstanceConfigRepository repo;
    private final RedisPoolFactory poolFactory;
    private final CryptoService crypto;

    // ==================================================================
    // CRUD
    // ==================================================================

    @Override
    public List<RedisInstanceConfigDto> listAll() {
        return repo.findAll().stream().map(this::toDto).toList();
    }

    @Override
    public RedisInstanceConfigDto getById(Long id) {
        RedisInstanceConfig entity = findOrThrow(id);
        RedisInstanceConfigDto dto = toDto(entity);
        boolean reachable;
        try {
            reachable = poolFactory.probe(entity, entity.decryptPassword(crypto));
        } catch (Exception e) {
            reachable = false;
        }
        return new RedisInstanceConfigDto(
                dto.id(), dto.name(), dto.host(), dto.port(), dto.hasPassword(),
                dto.dbIndex(), dto.timeoutMs(), dto.maxConnections(), dto.sshBastionId(),
                dto.remark(), dto.createTime(), dto.updateTime(), reachable
        );
    }

    @Override
    @Transactional
    public RedisInstanceConfigDto create(RedisInstanceConfigRequest req) {
        if (repo.existsByName(req.name())) {
            throw new BizException(ErrorCode.REDIS_NAME_DUPLICATED);
        }
        RedisInstanceConfig entity = new RedisInstanceConfig();
        applyRequest(entity, req, false);
        return toDto(repo.save(entity));
    }

    @Override
    @Transactional
    public RedisInstanceConfigDto update(Long id, RedisInstanceConfigRequest req) {
        RedisInstanceConfig entity = findOrThrow(id);
        if (!entity.getName().equals(req.name()) && repo.existsByName(req.name())) {
            throw new BizException(ErrorCode.REDIS_NAME_DUPLICATED);
        }
        applyRequest(entity, req, true);
        RedisInstanceConfig saved = repo.save(entity);
        poolFactory.rebuildPool(id);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        RedisInstanceConfig entity = findOrThrow(id);
        poolFactory.destroyPool(RedisPoolFactory.poolKey(id));
        repo.delete(entity);
    }

    @Override
    public boolean testConnection(Long id) {
        RedisInstanceConfig entity = findOrThrow(id);
        return poolFactory.probe(entity, entity.decryptPassword(crypto));
    }

    @Override
    public boolean testConnectionDirect(RedisInstanceConfigRequest req) {
        RedisInstanceConfig entity = new RedisInstanceConfig();
        applyRequest(entity, req, false);
        return poolFactory.probe(entity, req.password());
    }

    // ==================================================================
    // 可视化操作
    // ==================================================================

    @Override
    public RedisInfoDto info(Long id) {
        RedisInstanceConfig config = findOrThrow(id);
        String passwordPlain = config.decryptPassword(crypto);
        try (Jedis jedis = poolFactory.borrow(config, passwordPlain)) {
            jedis.select(config.getDbIndex());
            String rawInfo = jedis.info();
            return parseInfo(rawInfo, jedis.dbSize(), rawInfo);
        } catch (Exception e) {
            throw new BizException(ErrorCode.REDIS_CONNECT_FAILED, e.getMessage());
        }
    }

    @Override
    public RedisKeyListDto scanKeys(Long id, String db, String cursor, String pattern, Integer count) {
        RedisInstanceConfig config = findOrThrow(id);
        int dbIndex = parseInt(db, config.getDbIndex());
        String passwordPlain = config.decryptPassword(crypto);
        try (Jedis jedis = poolFactory.borrow(config, passwordPlain)) {
            jedis.select(dbIndex);
            ScanParams params = new ScanParams();
            String matchPattern = (pattern == null || pattern.isBlank()) ? "*" : pattern;
            params.match(matchPattern);
            params.count(count != null ? count : 200);
            ScanResult<String> result = jedis.scan(
                    cursor == null || cursor.isBlank() ? "0" : cursor, params);
            long dbSize = jedis.dbSize();
            return new RedisKeyListDto(result.getCursor(), result.getResult(), dbSize);
        } catch (Exception e) {
            throw new BizException(ErrorCode.REDIS_CONNECT_FAILED, e.getMessage());
        }
    }

    @Override
    public RedisValueDto getValue(Long id, String db, String key) {
        RedisInstanceConfig config = findOrThrow(id);
        int dbIndex = parseInt(db, config.getDbIndex());
        String passwordPlain = config.decryptPassword(crypto);
        try (Jedis jedis = poolFactory.borrow(config, passwordPlain)) {
            jedis.select(dbIndex);
            String type = jedis.type(key);
            if ("none".equalsIgnoreCase(type)) {
                Long ttl = jedis.ttl(key);
                return RedisValueDto.none(key, ttl);
            }
            Long ttl = jedis.ttl(key);
            return buildValueDto(jedis, key, type, ttl);
        } catch (Exception e) {
            return RedisValueDto.error(key, e.getMessage());
        }
    }

    @Override
    public List<String> listDbs(Long id) {
        RedisInstanceConfig config = findOrThrow(id);
        String passwordPlain = config.decryptPassword(crypto);
        try (Jedis jedis = poolFactory.borrow(config, passwordPlain)) {
            String keyspaceInfo = jedis.info("keyspace");
            int dbCount = 0;
            if (keyspaceInfo != null) {
                for (String line : keyspaceInfo.split("\n")) {
                    if (line.startsWith("db")) dbCount++;
                }
            }
            int total = Math.max(dbCount, 16);
            List<String> dbs = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                dbs.add("db" + i);
            }
            return dbs;
        } catch (Exception e) {
            List<String> dbs = new ArrayList<>(16);
            for (int i = 0; i < 16; i++) dbs.add("db" + i);
            return dbs;
        }
    }

    @Override
    public RedisExecResultDto execute(Long id, String db, String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return new RedisExecResultDto(false, commandLine, 0L, null, "命令不能为空");
        }
        String[] tokens = tokenize(commandLine.trim());
        ValidationResult vr = RedisCommandWhitelist.validate(tokens);
        if (!vr.allowed()) {
            throw new BizException(ErrorCode.REDIS_COMMAND_BLOCKED, vr.reason());
        }

        RedisInstanceConfig config = findOrThrow(id);
        int dbIndex = parseInt(db, config.getDbIndex());
        String passwordPlain = config.decryptPassword(crypto);
        try (Jedis jedis = poolFactory.borrow(config, passwordPlain)) {
            jedis.select(dbIndex);

            long start = System.nanoTime();
            Object raw = executeRaw(jedis, tokens);
            long costMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            String output = formatOutput(raw);
            return new RedisExecResultDto(true, commandLine, costMs, output, null);
        } catch (Exception e) {
            log.warn("[Redis] 命令执行失败 cmd={}: {}", commandLine, e.getMessage());
            return new RedisExecResultDto(false, commandLine, 0L, null, e.getMessage());
        }
    }

    @Override
    public RedisExecResultDto delKey(Long id, String db, String key) {
        RedisInstanceConfig config = findOrThrow(id);
        int dbIndex = parseInt(db, config.getDbIndex());
        String passwordPlain = config.decryptPassword(crypto);
        String commandLine = "DEL " + key;
        try (Jedis jedis = poolFactory.borrow(config, passwordPlain)) {
            jedis.select(dbIndex);
            long start = System.nanoTime();
            Long deleted = jedis.del(key);
            long costMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            return new RedisExecResultDto(true, commandLine, costMs,
                    deleted + " key(s) deleted", null);
        } catch (Exception e) {
            return new RedisExecResultDto(false, commandLine, 0L, null, e.getMessage());
        }
    }

    // ==================================================================
    // private helpers
    // ==================================================================

    private void applyRequest(RedisInstanceConfig entity, RedisInstanceConfigRequest req, boolean isUpdate) {
        entity.setName(req.name());
        entity.setHost(req.host());
        entity.setPort(req.effectivePort());
        entity.setDbIndex(req.effectiveDbIndex());
        entity.setTimeoutMs(req.effectiveTimeoutMs());
        entity.setMaxConnections(req.effectiveMaxConnections());
        entity.setSshBastionId(req.sshBastionId());
        entity.setRemark(req.remark());

        String pwd = req.password();
        if (!isUpdate) {
            entity.setPasswordCipher(crypto.encrypt(pwd));
        } else {
            if (pwd != null && !pwd.isBlank() && !CryptoService.MASK.equals(pwd)) {
                entity.setPasswordCipher(crypto.encrypt(pwd));
            }
        }
    }

    private RedisInstanceConfigDto toDto(RedisInstanceConfig e) {
        return new RedisInstanceConfigDto(
                e.getId(), e.getName(), e.getHost(), e.getPort(),
                e.getPasswordCipher() != null && !e.getPasswordCipher().isBlank(),
                e.getDbIndex(), e.getTimeoutMs(), e.getMaxConnections(),
                e.getSshBastionId(), e.getRemark(),
                e.getCreateTime(), e.getUpdateTime(), null
        );
    }

    private RedisInstanceConfig findOrThrow(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.REDIS_NOT_FOUND));
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private RedisValueDto buildValueDto(Jedis jedis, String key, String type, Long ttl) {
        switch (type.toLowerCase()) {
            case "string" -> {
                String val = jedis.get(key);
                return RedisValueDto.ok(key, "STRING", ttl, val, null, null);
            }
            case "hash" -> {
                Map<String, String> val = jedis.hgetAll(key);
                return RedisValueDto.ok(key, "HASH", ttl, null, val, null);
            }
            case "list" -> {
                List<String> val = jedis.lrange(key, 0, -1);
                return RedisValueDto.ok(key, "LIST", ttl, null, null, val);
            }
            case "set" -> {
                List<String> val = new ArrayList<>(jedis.smembers(key));
                return RedisValueDto.ok(key, "SET", ttl, null, null, val);
            }
            case "zset" -> {
                List<String> items = jedis.zrangeWithScores(key, 0, -1).stream()
                        .map(t -> t.getElement() + " (score=" + t.getScore() + ")")
                        .toList();
                return RedisValueDto.ok(key, "ZSET", ttl, null, null, items);
            }
            case "stream" -> {
                String summary;
                try {
                    summary = jedis.xinfoStream(key).toString();
                } catch (Exception ignored) {
                    summary = "Stream key, use raw command to inspect";
                }
                return RedisValueDto.ok(key, "STREAM", ttl, summary, null, null);
            }
            default -> {
                return RedisValueDto.ok(key, type.toUpperCase(), ttl, null, null, null);
            }
        }
    }

    private RedisInfoDto parseInfo(String rawInfo, long dbSize, String raw) {
        Map<String, String> flat = new LinkedHashMap<>();
        String currentSection = "";
        for (String line : rawInfo.split("\n")) {
            String l = line.trim();
            if (l.startsWith("# ")) {
                currentSection = l.substring(2).trim();
                continue;
            }
            int idx = l.indexOf(':');
            if (idx > 0) {
                String k = l.substring(0, idx).trim();
                String v = l.substring(idx + 1).trim();
                flat.put(currentSection + "." + k, v);
            }
        }
        String version = flat.getOrDefault("server.redis_version", "unknown");
        String mode = flat.getOrDefault("server.redis_mode",
                flat.getOrDefault("replication.role", "standalone"));
        long clients = parseLong(flat.get("clients.connected_clients"), 0);
        String memRaw = flat.getOrDefault("memory.used_memory_human", "?");
        long commands = parseLong(flat.get("stats.total_commands_processed"), 0);
        return new RedisInfoDto(version, mode, clients, memRaw, commands, dbSize, rawInfo);
    }

    private static long parseLong(String s, long fallback) {
        if (s == null) return fallback;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static String[] tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }
            if ((c == ' ' || c == '\t') && !inSingle && !inDouble) {
                if (!sb.isEmpty()) { tokens.add(sb.toString()); sb.setLength(0); }
            } else {
                sb.append(c);
            }
        }
        if (!sb.isEmpty()) tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }

    private static Object executeRaw(Jedis jedis, String[] tokens) {
        String cmd = tokens[0].toUpperCase();
        return switch (cmd) {
            case "PING" -> jedis.ping();
            case "INFO" -> tokens.length > 1 ? jedis.info(tokens[1]) : jedis.info();
            case "DBSIZE" -> jedis.dbSize();
            case "KEYS" -> jedis.keys(tokens.length > 1 ? tokens[1] : "*");
            case "EXISTS" -> jedis.exists(tokens[1]);
            case "DEL" -> tokens.length > 2
                    ? jedis.del(Arrays.copyOfRange(tokens, 1, tokens.length))
                    : jedis.del(tokens[1]);
            case "UNLINK" -> tokens.length > 2
                    ? jedis.unlink(Arrays.copyOfRange(tokens, 1, tokens.length))
                    : jedis.unlink(tokens[1]);
            case "TYPE" -> jedis.type(tokens[1]);
            case "TTL" -> jedis.ttl(tokens[1]);
            case "PTTL" -> jedis.pttl(tokens[1]);
            case "GET" -> jedis.get(tokens[1]);
            case "SET" -> {
                if (tokens.length >= 3) { jedis.set(tokens[1], tokens[2]); yield "OK"; }
                yield null;
            }
            case "APPEND" -> tokens.length >= 3 ? jedis.append(tokens[1], tokens[2]) : null;
            case "STRLEN" -> jedis.strlen(tokens[1]);
            case "INCR" -> jedis.incr(tokens[1]);
            case "DECR" -> jedis.decr(tokens[1]);
            case "EXPIRE" -> tokens.length >= 3 ? jedis.expire(tokens[1], Integer.parseInt(tokens[2])) : null;
            case "HGET" -> jedis.hget(tokens[1], tokens[2]);
            case "HMGET" -> jedis.hmget(tokens[1], Arrays.copyOfRange(tokens, 2, tokens.length));
            case "HGETALL" -> jedis.hgetAll(tokens[1]);
            case "HKEYS" -> jedis.hkeys(tokens[1]);
            case "HVALS" -> jedis.hvals(tokens[1]);
            case "HLEN" -> jedis.hlen(tokens[1]);
            case "HSET" -> {
                if (tokens.length >= 4) { jedis.hset(tokens[1], tokens[2], tokens[3]); yield "OK"; }
                yield null;
            }
            case "HDEL" -> tokens.length >= 3
                    ? jedis.hdel(tokens[1], Arrays.copyOfRange(tokens, 2, tokens.length))
                    : null;
            case "HINCRBY" -> tokens.length >= 4 ? jedis.hincrBy(tokens[1], tokens[2], Long.parseLong(tokens[3])) : null;
            case "LPUSH" -> jedis.lpush(tokens[1], Arrays.copyOfRange(tokens, 2, tokens.length));
            case "RPUSH" -> jedis.rpush(tokens[1], Arrays.copyOfRange(tokens, 2, tokens.length));
            case "LPOP" -> jedis.lpop(tokens[1]);
            case "RPOP" -> jedis.rpop(tokens[1]);
            case "LLEN" -> jedis.llen(tokens[1]);
            case "LRANGE" -> jedis.lrange(tokens[1], Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]));
            case "SADD" -> jedis.sadd(tokens[1], Arrays.copyOfRange(tokens, 2, tokens.length));
            case "SMEMBERS" -> jedis.smembers(tokens[1]);
            case "SCARD" -> jedis.scard(tokens[1]);
            case "SISMEMBER" -> jedis.sismember(tokens[1], tokens[2]);
            case "SREM" -> jedis.srem(tokens[1], Arrays.copyOfRange(tokens, 2, tokens.length));
            case "ZADD" -> {
                if (tokens.length >= 4) { jedis.zadd(tokens[1], Double.parseDouble(tokens[2]), tokens[3]); yield "OK"; }
                yield null;
            }
            case "ZRANGE" -> jedis.zrange(tokens[1], Integer.parseInt(tokens[2]), Integer.parseInt(tokens[3]));
            case "ZRANGEBYSCORE" -> jedis.zrangeByScore(tokens[1], tokens[2], tokens[3]);
            case "ZCARD" -> jedis.zcard(tokens[1]);
            case "ZREM" -> jedis.zrem(tokens[1], Arrays.copyOfRange(tokens, 2, tokens.length));
            case "AUTH" -> {
                if (tokens.length >= 2) { jedis.auth(tokens[1]); yield "OK"; }
                yield null;
            }
            case "SELECT" -> {
                if (tokens.length >= 2) { jedis.select(Integer.parseInt(tokens[1])); yield "OK"; }
                yield null;
            }
            case "QUIT" -> "OK";
            default -> throw new UnsupportedOperationException("未实现命令分发: " + cmd);
        };
    }

    static String formatOutput(Object raw) {
        if (raw == null) return "(nil)";
        if (raw instanceof String s) return s;
        if (raw instanceof Number n) return n.toString();
        if (raw instanceof Boolean b) return b ? "1" : "0";
        if (raw instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                sb.append(i + 1).append(") ").append(list.get(i)).append('\n');
            }
            return sb.toString().trim();
        }
        if (raw instanceof Set<?> set) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Object o : set) sb.append(i++).append(") ").append(o).append('\n');
            return sb.toString().trim();
        }
        if (raw instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sb.append(i++).append(") ").append(e.getKey()).append('\n');
                sb.append(i++).append(") ").append(e.getValue()).append('\n');
            }
            return sb.toString().trim();
        }
        return raw.toString();
    }
}
