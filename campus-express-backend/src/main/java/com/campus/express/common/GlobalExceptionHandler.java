package com.campus.express.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：所有异常统一转成 Result 结构返回，前端只按 code 分支处理
 *
 * 错误码约定（全项目统一，前端据 code 分支处理）：
 *   200 成功
 *   400 参数校验失败 / 业务规则拒绝（BusinessException 默认码）
 *   401 未登录或令牌失效（JwtInterceptor 直接返回，不进入本处理器）
 *   403 无权限 / 越权操作（BusinessException 指定 403）
 *   500 系统异常（兜底，仅记录日志，不向前端暴露堆栈）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：返回业务提示（抢单失败、状态非法、无权限等） */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("[业务异常] code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 参数校验异常（@Valid）：拼接第一个字段错误信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getDefaultMessage();
        return Result.error(400, msg);
    }

    /** 兜底异常：记录日志，返回统一 500，不向前端暴露堆栈 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("[系统异常]", e);
        return Result.error(500, "服务器开小差了，请稍后重试");
    }
}
