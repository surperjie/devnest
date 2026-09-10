-- V11: 数据源增加归属与可见性字段(为多人共用预留).
-- 单机形态:owner 写入本机用户名,visibility 默认 PUBLIC,现有行为不变.
ALTER TABLE data_source_config ADD COLUMN IF NOT EXISTS owner VARCHAR(64);
ALTER TABLE data_source_config ADD COLUMN IF NOT EXISTS visibility VARCHAR(16) DEFAULT 'PUBLIC';
