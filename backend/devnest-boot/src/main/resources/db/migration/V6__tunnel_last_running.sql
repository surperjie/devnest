-- V6: ssh_port_mapping 增加 last_running 字段,用于服务重启时恢复启动状态
-- 1=启动中(用户上次点了启动),0=停止或未启动
ALTER TABLE ssh_port_mapping ADD COLUMN last_running BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN ssh_port_mapping.last_running IS '服务重启时是否继承上次启动状态(1=启动,0=停止)';
-- H2/MySQL 兼容:建索引便于启动扫描
CREATE INDEX idx_ssh_port_mapping_last_running ON ssh_port_mapping (last_running);
