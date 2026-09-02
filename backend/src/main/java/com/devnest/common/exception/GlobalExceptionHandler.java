package com.devnest.common.exception;

import com.devnest.common.response.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理,统一转 ApiResult.
 *
 * @Author Ajiejiejie
 * @Date 2026/9/1 15:30
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBiz(BizException e) {
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ApiResult.fail(ErrorCode.PARAM_INVALID.code(),
                ErrorCode.PARAM_INVALID.msg() + ": " + detail);
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception e) {
        log.error("未捕获异常", e);
        return ApiResult.fail(ErrorCode.INTERNAL_ERROR.code(),
                ErrorCode.INTERNAL_ERROR.msg() + ": " + e.getMessage());
    }
}
