-- V7: Redis 实例配置表
-- 支持绑定 SSH 隧道 (可选,devnest-tunnel SPI 端口转发)
-- 密码 AES-256-GCM 加密存储
-- H2/MySQL 兼容

CREATE TABLE redis_instance_config (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL,
    host            VARCHAR(128) NOT NULL,
    port            INT          NOT NULL DEFAULT 6379,
    password_cipher VARCHAR(512),                  -- AES 加密后密码,可为空(无密码)
    db_index        INT          NOT NULL DEFAULT 0,
    timeout_ms      INT          NOT NULL DEFAULT 2000,
    max_connections INT          NOT NULL DEFAULT 8,
    ssh_bastion_id  BIGINT,                       -- 可选:绑定的 SSH 跳板,Redis 连接走本地端口转发
    remark          VARCHAR(255),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_redis_instance_config_name UNIQUE (name)
);

CREATE INDEX idx_redis_instance_bastion ON redis_instance_config (ssh_bastion_id);
