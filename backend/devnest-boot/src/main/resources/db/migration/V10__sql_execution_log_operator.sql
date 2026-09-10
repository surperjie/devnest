-- V10: SQL 执行日志增加操作者字段.
-- 单机形态写入本机登录用户名;将来后端多人共用时写入登录用户,用于审计追溯.
ALTER TABLE sql_execution_log ADD COLUMN IF NOT EXISTS operator VARCHAR(64);
