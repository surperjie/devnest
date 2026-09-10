-- ============================================================
-- DevNest V8: 流水线(Pipeline)模块
-- ------------------------------------------------------------
-- 定义(多步脚本) + 运行实例 + 步骤运行实例
-- 约束:同一条流水线同一时刻只允许 1 个活动运行(WAITING/RUNNING),由服务层加锁保证
-- H2(MySQL 模式)/MySQL 8 兼容
-- ============================================================

-- 流水线定义
CREATE TABLE pipeline_definition (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(64)  NOT NULL,
    workdir      VARCHAR(512),                          -- 默认工作目录(步骤未单独配置时使用),可含 {{变量}}
    remark       VARCHAR(255),
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_pipeline_definition_name UNIQUE (name)
);

-- 流水线步骤(顺序执行,seq 升序)
CREATE TABLE pipeline_step (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    pipeline_id   BIGINT       NOT NULL,
    seq           INT          NOT NULL,
    name          VARCHAR(128) NOT NULL,
    run_type      VARCHAR(16)  NOT NULL DEFAULT 'batch',  -- batch|cmdline|powershell|python
    script        TEXT         NOT NULL,                  -- 脚本内容/命令行(支持 {{ctx变量}})
    workdir       VARCHAR(512),                           -- 步骤工作目录,空=继承流水线 workdir/运行工作区
    args          TEXT,                                   -- 附加参数 JSON 数组,依次作为 %1/%2/$args
    timeout_sec   INT          NOT NULL DEFAULT 300,
    on_fail       VARCHAR(16)  NOT NULL DEFAULT 'abort',  -- abort|continue
    output_parse  VARCHAR(16)  NOT NULL DEFAULT 'none',   -- none|keyvalue(stdout 解析 KEY=VALUE 进上下文)
    enabled       INT          NOT NULL DEFAULT 1,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_pipeline_step_pipeline (pipeline_id)
);

-- 流水线运行实例
CREATE TABLE pipeline_run (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    pipeline_id    BIGINT       NOT NULL,
    pipeline_name  VARCHAR(64)  NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'WAITING', -- WAITING|RUNNING|SUCCESS|FAILED|STOPPED
    inputs         TEXT,                                    -- 运行入参 JSON 对象
    workspace_dir  VARCHAR(512),                            -- 本次运行的工作区目录(脚本/日志落点)
    error_message  VARCHAR(1000),
    start_time     TIMESTAMP,
    end_time       TIMESTAMP,
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_pipeline_run_pipeline (pipeline_id),
    INDEX idx_pipeline_run_status (status)
);

-- 步骤运行实例
CREATE TABLE pipeline_step_run (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    run_id         BIGINT       NOT NULL,
    seq            INT          NOT NULL,
    name           VARCHAR(128) NOT NULL,
    run_type       VARCHAR(16)  NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'RUNNING', -- RUNNING|SUCCESS|FAILED|SKIPPED
    exit_code      INT,
    error_message  VARCHAR(1000),
    start_time     TIMESTAMP,
    end_time       TIMESTAMP,
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_pipeline_step_run_run (run_id)
);
