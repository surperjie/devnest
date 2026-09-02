-- ============================================================
-- DevNest 数据库初始化脚本(MySQL 8)
-- ------------------------------------------------------------
-- 用途:
--   1. 首次手动建库 + 建表(Flyway 不管建库,只管建表)
--   2. 重置开发环境
--   3. 作为表结构参考
--
-- 日常启动说明:
--   后端启动时 Flyway 会自动执行 V1__init_schema.sql 建表,
--   本脚本仅用于首次建库或手动重置,日常无需重复执行。
--
-- 字符集:utf8mb4(完整 Unicode,含 emoji)
-- 引擎:InnoDB(支持事务 + 外键)
-- ============================================================

-- 1. 创建数据库(如不存在)
CREATE DATABASE IF NOT EXISTS dev_nest
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

-- 2. 切换数据库
USE dev_nest;

-- 3.(可选)重置环境:取消注释下面三行可清空已有表和数据
--    生产环境慎用!会删除所有跳板配置。
-- DROP TABLE IF EXISTS ssh_port_mapping;
-- DROP TABLE IF EXISTS ssh_bastion;
-- DROP TABLE IF EXISTS flyway_schema_history;

-- 4. 建表:SSH 跳板配置
--    存的是 DevNest 自身配置(经 AES-256-GCM 加密),非用户业务数据
CREATE TABLE IF NOT EXISTS ssh_bastion (
    id           BIGINT       NOT NULL AUTO_INCREMENT                                   COMMENT '主键',
    name         VARCHAR(64)  NOT NULL                                                   COMMENT '跳板名称(唯一)',
    ssh_host     VARCHAR(128) NOT NULL                                                   COMMENT 'SSH 跳板机地址',
    ssh_port     INT          NOT NULL DEFAULT 22                                        COMMENT 'SSH 端口',
    ssh_user     VARCHAR(64)  NOT NULL                                                   COMMENT 'SSH 用户名',
    ssh_password VARCHAR(512) NOT NULL                                                   COMMENT 'SSH 密码密文(AES-256-GCM + base64,不可逆,不用明文)',
    remark       VARCHAR(255)                                                           COMMENT '备注',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP                         COMMENT '创建时间(Hibernate @CreationTimestamp 维护)',
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(Hibernate @UpdateTimestamp 维护,DB 兜底)',
    PRIMARY KEY (id),
    CONSTRAINT uk_ssh_bastion_name UNIQUE (name)  -- 跳板名称唯一,业务层重名校验兜底
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SSH 跳板配置';

-- 5. 建表:端口映射模板(一对多,属跳板)
--    一个跳板可绑定多条端口映射,跳板删除时级联删除映射
CREATE TABLE IF NOT EXISTS ssh_port_mapping (
    id                   BIGINT       NOT NULL AUTO_INCREMENT                   COMMENT '主键',
    bastion_id           BIGINT       NOT NULL                                  COMMENT '所属跳板 ID',
    remote_host          VARCHAR(128) NOT NULL                                  COMMENT '远端内网主机(IP)',
    remote_port          INT          NOT NULL                                  COMMENT '远端端口',
    preferred_local_port INT                                                    COMMENT '期望本地端口(NULL 则运行时自动扫描分配)',
    label                VARCHAR(64)                                            COMMENT '映射备注(如:测试库)',
    create_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
    update_time          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT fk_ssh_port_mapping_bastion
        FOREIGN KEY (bastion_id) REFERENCES ssh_bastion (id) ON DELETE CASCADE,  -- 跳板删除时级联删映射
    INDEX idx_ssh_port_mapping_bastion (bastion_id)  -- 按 bastion_id 查询频繁,加索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SSH 端口映射模板';

-- 6. 验证(可选,执行后看结果确认)
-- SHOW TABLES;
-- DESC ssh_bastion;
-- DESC ssh_port_mapping;

-- ============================================================
-- 后续模块将追加的表(二期 SSH 控制台 / 三期数据库 / 四期 Redis / 五期 HTTP)
-- 不在此脚本中,由对应模块的 Flyway 迁移脚本 V2/V3/... 自动创建
-- 详见 docs/architecture/数据模型.md
-- ============================================================
