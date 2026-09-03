package com.devnest.redis.security;

import redis.clients.jedis.Protocol;

import java.util.Locale;
import java.util.Set;

/**
 * Redis 命令安全白名单.
 *
 * 策略:
 * - 显式枚举允许的命令,一律大写比对
 * - 危险命令(BGSAVE/FLUSHALL/FLUSHDB/KEYS/SHUTDOWN/CONFIG/DEBUG/OBJECT/REPLCONF/MIGRATE/SWAPDB)直接拒绝
 * - 其他未知命令默认拒绝,遵循 Fail-Closed
 *
 * @Author Ajiejiejie
 * @Date 2026/9/3 14:10
 */
public final class RedisCommandWhitelist {

    private RedisCommandWhitelist() {}

    /** 只读类命令 - 安全无副作用 */
    private static final Set<String> READ_COMMANDS = Set.of(
            // key 操作(只读)
            "GET", "MGET", "TYPE", "EXISTS", "EXPIRETIME", "PEXPIRETIME",
            "TTL", "PTTL", "RANDOMKEY", "SCAN",
            // string
            "STRLEN", "GETRANGE",
            // hash
            "HGET", "HMGET", "HGETALL", "HKEYS", "HVALS", "HLEN", "HEXISTS", "HSCAN", "HRANDFIELD",
            // list
            "LLEN", "LINDEX", "LRANGE", "LPOS",
            // set
            "SCARD", "SMEMBERS", "SISMEMBER", "SINTER", "SINTERSTORE",
            "SUNION", "SUNIONSTORE", "SDIFF", "SDIFFSTORE", "SRANDMEMBER", "SSCAN",
            // zset
            "ZCARD", "ZCOUNT", "ZRANGE", "ZREVRANGE", "ZRANGEBYSCORE", "ZREVRANGEBYSCORE",
            "ZRANK", "ZREVRANK", "ZSCORE", "ZINCRBY", "ZSCAN", "ZDIFF", "ZINTER", "ZUNION",
            "ZRANDMEMBER",
            // geo
            "GEODIST", "GEORADIUS", "GEORADIUSBYMEMBER", "GEOHASH", "GEOPOS",
            // stream
            "XLEN", "XRANGE", "XREVRANGE", "XREAD", "XINFO", "XREADGROUP",
            // hyperloglog
            "PFCOUNT",
            // bitmap
            "GETBIT", "BITCOUNT", "BITPOS",
            // database
            "SELECT", "DBSIZE", "INFO", "PING", "ECHO", "TIME", "HELLO",
            // server(安全只读子集)
            "CLIENT LIST", "CLIENT GETNAME", "CLUSTER INFO", "CLUSTER NODES",
            "LATENCY LATEST", "LATENCY HISTORY", "MEMORY USAGE", "MEMORY STATS",
            "MODULE LIST", "SLOWLOG GET", "SLOWLOG LEN", "PUBSUB CHANNELS", "PUBSUB NUMSUB",
            // script(只读执行)
            "EVAL_RO", "EVALSHA_RO"
    );

    /** 写类命令 - 允许但有副作用,需要前端用户明确操作 */
    private static final Set<String> WRITE_COMMANDS = Set.of(
            // key 操作(写)
            "SET", "MSET", "DEL", "EXPIRE", "EXPIREAT", "PEXPIRE", "PEXPIREAT",
            "PERSIST", "RENAME", "RENAMENX", "COPY", "RESTORE", "RESTORE-ASKING", "UNLINK",
            "TOUCH",
            // string
            "APPEND", "SETRANGE", "SETBIT", "SETNX", "SETEX", "PSETEX", "INCR", "DECR",
            "INCRBY", "DECRBY", "INCRBYFLOAT",
            // hash
            "HSET", "HMSET", "HSETNX", "HDEL", "HINCRBY", "HINCRBYFLOAT", "HSTRLEN",
            // list
            "LPUSH", "RPUSH", "LPUSHX", "RPUSHX", "LINSERT", "LSET", "LTRIM",
            "LPOP", "RPOP", "BLPOP", "BRPOP", "LMOVE", "BLMOVE",
            // set
            "SADD", "SREM", "SPOP", "SMOVE",
            // zset
            "ZADD", "ZREM", "ZPOPMIN", "ZPOPMAX", "BZPOPMIN", "BZPOPMAX", "ZREMRAWSCORE",
            // stream
            "XADD", "XDEL", "XTRIM", "XACK", "XCLAIM", "XAUTOCLAIM", "XGROUP",
            // hyperloglog
            "PFADD", "PFMERGE",
            // bitmap
            "SETBIT", "BITOP",
            // transaction
            "MULTI", "EXEC", "DISCARD", "WATCH", "UNWATCH",
            // connection
            "QUIT", "AUTH", "SELECT"
    );

    /** 明确拒绝的危险命令(即使在允许列表中也覆盖拦截) */
    private static final Set<String> DANGEROUS_COMMANDS = Set.of(
            "FLUSHALL", "FLUSHDB", "SHUTDOWN", "BGSAVE", "BGREWRITEAOF", "SAVE",
            "CONFIG SET", "CONFIG REWRITE", "DEBUG", "MIGRATE", "SWAPDB",
            "KEYS", // KEYS 在大数据集上会阻塞,用 SCAN 替代
            "OBJECT", // 暴露内部状态
            "REPLCONF", "PSYNC", "SYNC", // 复制相关
            "FAILOVER", "CLUSTER MEET", "CLUSTER FORGET", "CLUSTER RESET",
            "CLIENT PAUSE", "CLIENT UNPAUSE",
            "ACL SETUSER", "ACL DELUSER", "ACL RESET",
            "FUNCTION FLUSH", "FUNCTION DELETE", "FUNCTION LOAD",
            "MODULE LOAD", "MODULE UNLOAD", "MODULE RELOAD"
    );

    /**
     * 校验命令是否允许执行.
     *
     * @param commandArgs 命令参数数组(第一个元素是命令名)
     * @return 校验结果
     */
    public static ValidationResult validate(String[] commandArgs) {
        if (commandArgs == null || commandArgs.length == 0) {
            return ValidationResult.reject("命令为空");
        }
        // 规范化命令名(多词命令如 "CLIENT LIST" 需要拼接)
        String normalized = normalizeCommand(commandArgs);

        // 危险命令优先拦截
        for (String dangerous : DANGEROUS_COMMANDS) {
            if (normalized.equals(dangerous)) {
                return ValidationResult.reject("危险命令已被拦截: " + dangerous);
            }
        }

        if (READ_COMMANDS.contains(normalized)) {
            return ValidationResult.ok(CommandType.READ);
        }
        if (WRITE_COMMANDS.contains(normalized)) {
            return ValidationResult.ok(CommandType.WRITE);
        }
        return ValidationResult.reject("不在白名单中的命令: " + normalized);
    }

    private static String normalizeCommand(String[] args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(args.length, 3); i++) {
            if (i > 0) sb.append(' ');
            sb.append(args[i].toUpperCase(Locale.ROOT));
        }
        // 单条命令只取第一个词
        String firstWord = args[0].toUpperCase(Locale.ROOT);
        // 两词组合命令的白名单需要精确匹配(如 "CLIENT LIST")
        if (args.length > 1) {
            String twoWord = firstWord + " " + args[1].toUpperCase(Locale.ROOT);
            if (READ_COMMANDS.contains(twoWord) || WRITE_COMMANDS.contains(twoWord)
                    || DANGEROUS_COMMANDS.contains(twoWord)) {
                return twoWord;
            }
        }
        return firstWord;
    }

    /** 直接从 Protocol.Command 枚举名(与 Jedis 内部一致)查找安全性. */
    public static ValidationResult validateByEnum(Protocol.Command cmd) {
        return validate(new String[]{cmd.name()});
    }

    /** 校验结果封装 */
    public record ValidationResult(boolean allowed, CommandType type, String reason) {
        public static ValidationResult ok(CommandType type) {
            return new ValidationResult(true, type, null);
        }
        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, null, reason);
        }
    }

    public enum CommandType { READ, WRITE }
}
