package com.devnest.common.exception;

/**
 * 业务异常,携带错误码与消息.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.msg());
        this.code = errorCode.code();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(errorCode.msg() + ": " + detail);
        this.code = errorCode.code();
    }

    public int getCode() {
        return code;
    }
}
