-- 三期:数据源配置表 + SQL执行日志表
CREATE TABLE IF NOT EXISTS data_source_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE,
    db_type VARCHAR(16) NOT NULL,
    host VARCHAR(128) NOT NULL,
    port INT NOT NULL DEFAULT 3306,
    database_name VARCHAR(128) NOT NULL,
    username VARCHAR(64) NOT NULL,
    password_cipher VARCHAR(512) NOT NULL,
    tunnel_bastion_id BIGINT,
    remark VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sql_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id BIGINT NOT NULL,
    datasource_name VARCHAR(64) NOT NULL,
    sql_text TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_msg VARCHAR(500),
    cost_ms BIGINT,
    row_count INT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
