package com.devnest.common.exception;

import com.devnest.common.response.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理 - 安全版.
 * <p>
 * 1) 业务异常(BizException)原样返回业务错误码和业务提示(不包含任何堆栈/实现细节).
 * 2) 参数校验类异常:仅返回字段名+提示,不带 toString/内部对象信息.
 * 3) 其它未捕获异常:
 *    - 服务端写完整堆栈 ERROR 日志(排障需要),关联 requestId/URL
 *    - 响应给前端固定文案:"系统异常,请稍后重试",绝对不返回 e.getMessage()
 *      (防 NullPointerException/ArrayIndexOutOfBounds/SQL 错误等泄露实现细节)
 *
 * @Author Ajiejiejie
 * @Date 2026/9/2 16:00
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResult<Void> handleBiz(BizException e, HttpServletRequest req) {
        log.warn("[Biz] {} {} -> {} {}",
                req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + nullSafe(f.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        log.warn("[Valid] {} {} -> {}", req.getMethod(), req.getRequestURI(), detail);
        return ApiResult.fail(ErrorCode.PARAM_INVALID.code(),
                ErrorCode.PARAM_INVALID.msg() + ": " + detail);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> handleBadRequest(Exception e, HttpServletRequest req) {
        log.warn("[BadReq] {} {} -> {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        return ApiResult.fail(ErrorCode.PARAM_INVALID.code(),
                ErrorCode.PARAM_INVALID.msg());
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNotFound(Exception e) {
        return ApiResult.fail(4040, "NOT_FOUND");
    }

    /**
     * 兜底异常 - 绝不向浏览器泄露堆栈或 message.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleOther(Exception e, HttpServletRequest req) {
        log.error("[InternalError] {} {}", req.getMethod(), req.getRequestURI(), e);
        return ApiResult.fail(ErrorCode.INTERNAL_ERROR.code(),
                "系统异常,请稍后重试");
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
