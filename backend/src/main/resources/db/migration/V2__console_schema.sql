-- ============================================================
-- DevNest V2: SSH 远程控制台会话表(二期)
-- ------------------------------------------------------------
-- 存储远程控制台连接配置:可直连或经一期跳板隧道连内网主机
-- 运行时通过 WebSocket + JSch ChannelShell 提供 PTY 交互终端
-- ============================================================

CREATE TABLE remote_console_session (
    id           BIGINT       NOT NULL AUTO_INCREMENT                                   COMMENT '主键',
    name         VARCHAR(64)  NOT NULL                                                   COMMENT '控制台名称(唯一)',
    bastion_id   BIGINT                                                                  COMMENT '绑定的跳板ID(NULL=直连模式,非NULL=经该跳板隧道连接)',
    remote_host  VARCHAR(128) NOT NULL                                                   COMMENT '目标主机(直连=目标host,隧道=远端内网host)',
    remote_port  INT          NOT NULL DEFAULT 22                                        COMMENT '目标SSH端口',
    ssh_user     VARCHAR(64)  NOT NULL                                                   COMMENT 'SSH用户名',
    ssh_password VARCHAR(512) NOT NULL                                                   COMMENT 'SSH密码密文(AES-256-GCM + base64)',
    remark       VARCHAR(255)                                                            COMMENT '备注',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP                         COMMENT '创建时间',
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_remote_console_name UNIQUE (name),
    INDEX idx_remote_console_bastion (bastion_id)
);

-- 注:bastion_id 不加外键约束,允许跳板删除后控制台保留(启动时检测隧道缺失则报错)
