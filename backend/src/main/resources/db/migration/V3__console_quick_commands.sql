-- ============================================================
-- DevNest V3: 远程控制台加快捷命令字段
-- ------------------------------------------------------------
-- quick_commands 存 JSON 数组,格式:
--   [{"name":"查日志","command":"tail -f /var/log/app.log"},
--    {"name":"重启","command":"systemctl restart app"}]
-- 绑定到单个控制台,终端弹窗以按钮形式展示,点击即发送到 shell
-- ============================================================

ALTER TABLE remote_console_session ADD COLUMN quick_commands TEXT;
