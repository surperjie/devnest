package com.devnest.common.exception;

/**
 * 业务错误码定义.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public enum ErrorCode {

    BASTION_NOT_FOUND(1001, "跳板配置不存在"),
    BASTION_NAME_DUPLICATED(1002, "跳板名称重复"),
    PORT_MAPPING_NOT_FOUND(1003, "端口映射不存在"),
    PORT_ALLOCATE_FAILED(1004, "无可用本地端口"),
    TUNNEL_START_FAILED(1005, "隧道启动失败"),
    TUNNEL_NOT_RUNNING(1006, "隧道未运行"),
    TUNNEL_ALREADY_RUNNING(1007, "隧道已运行"),
    TUNNEL_ALLOCATE_FAILED(1008, "隧道动态端口转发建立失败"),

    CONSOLE_NOT_FOUND(2001, "控制台配置不存在"),
    CONSOLE_NAME_DUPLICATED(2002, "控制台名称重复"),
    WS_TOKEN_INVALID(2003, "WebSocket 握手 token 无效"),

    DATASOURCE_NOT_FOUND(3001, "数据源配置不存在"),
    DATASOURCE_NAME_DUPLICATED(3002, "数据源名称重复"),
    DATASOURCE_CONNECT_FAILED(3003, "数据源连接失败"),
    DATASOURCE_UNSUPPORTED_TYPE(3004, "不支持的数据库类型(驱动未安装)"),
    SQL_BLOCKED_BY_WHITELIST(3010, "SQL 被白名单拦截(仅允许 SELECT/SHOW/DESCRIBE/EXPLAIN)"),
    SQL_EXECUTE_FAILED(3011, "SQL 执行失败"),
    SQL_RESULT_TOO_LARGE(3012, "SQL 返回行数超过上限,请加 LIMIT"),

    AI_DISABLED(3501, "AI 功能未配置或未启用"),
    AI_GENERATE_FAILED(3502, "AI SQL 生成失败"),

    PARAM_INVALID(4000, "参数校验失败"),
    INTERNAL_ERROR(5000, "系统异常");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int code() {
        return code;
    }

    public String msg() {
        return msg;
    }
}
