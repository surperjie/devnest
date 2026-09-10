-- ============================================================
-- DevNest V9: 流水线定义增加"默认变量"
-- vars 存 JSON 对象,如 {"pdDate":"2026/8/25","tag":"DTB_WEB_xxx"}
-- 运行上下文合并顺序:默认变量 -> 运行入参(同名覆盖) -> 内置 run.*
-- H2(MySQL 模式)/MySQL 8 兼容
-- ============================================================
ALTER TABLE pipeline_definition ADD COLUMN vars TEXT NULL;
