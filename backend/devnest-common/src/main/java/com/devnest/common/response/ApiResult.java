package com.devnest.common.response;

/**
 * 统一响应体封装.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
public record ApiResult<T>(int code, String msg, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(0, "ok", null);
    }

    public static <T> ApiResult<T> fail(int code, String msg) {
        return new ApiResult<>(code, msg, null);
    }
}
