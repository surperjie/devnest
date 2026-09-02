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
