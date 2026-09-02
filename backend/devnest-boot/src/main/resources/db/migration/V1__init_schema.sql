-- DevNest 一期初始化 schema:SSH 跳板 + 端口映射
-- 兼容 H2(MySQL 模式)与 MySQL 8(库级字符集 utf8mb4)
-- 时间字段由 Hibernate @CreationTimestamp/@UpdateTimestamp 维护,DDL 仅设默认值兜底

CREATE TABLE ssh_bastion (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(64)  NOT NULL,
    ssh_host        VARCHAR(128) NOT NULL,
    ssh_port        INT          NOT NULL DEFAULT 22,
    ssh_user        VARCHAR(64)  NOT NULL,
    ssh_password    VARCHAR(512) NOT NULL,
    remark          VARCHAR(255),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_ssh_bastion_name UNIQUE (name)
);

CREATE TABLE ssh_port_mapping (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    bastion_id           BIGINT       NOT NULL,
    remote_host          VARCHAR(128) NOT NULL,
    remote_port         INT          NOT NULL,
    preferred_local_port INT,
    label                VARCHAR(64),
    create_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ssh_port_mapping_bastion
        FOREIGN KEY (bastion_id) REFERENCES ssh_bastion (id) ON DELETE CASCADE
);

CREATE INDEX idx_ssh_port_mapping_bastion ON ssh_port_mapping (bastion_id);
