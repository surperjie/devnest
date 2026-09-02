-- V5: database_name 改为可空(支持不指定库名,查看整个数据库服务器)
ALTER TABLE data_source_config ALTER COLUMN database_name DROP NOT NULL;
