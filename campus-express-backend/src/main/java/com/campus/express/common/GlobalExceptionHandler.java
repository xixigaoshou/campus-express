package com.campus.express.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

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

    /** 控制器方法参数约束校验（Spring 6.1 内建：@RequestParam 上的 @Min/@Max 等） */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleMethodValidation(HandlerMethodValidationException e) {
        String msg = e.getAllValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(400, msg);
    }

    /** AOP 方法级约束校验（@Validated + 约束注解） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(400, msg);
    }

    /**
     * 唯一索引冲突：注册/改手机号/评价等「先查后插」在并发下仍可能撞库，
     * 由数据库唯一索引兜底后在此转成可读的 400，而不是落到兜底分支变成 500
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        String raw = String.valueOf(e.getMessage());
        String msg;
        if (raw.contains("uk_student_no")) {
            msg = "该学号已注册，请直接登录";
        } else if (raw.contains("uk_phone")) {
            msg = "该手机号已被注册";
        } else if (raw.contains("uk_order_evaluator")) {
            msg = "您已评价过该订单";
        } else if (raw.contains("uk_active_pickup")) {
            msg = "该取件码在取件地点已被使用，请更换取件码";
        } else {
            msg = "数据已存在，请勿重复提交";
        }
        log.warn("[唯一约束冲突] {}", msg);
        return Result.error(400, msg);
    }

    /** 兜底异常：记录日志，返回统一 500，不向前端暴露堆栈 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("[系统异常]", e);
        return Result.error(500, "服务器开小差了，请稍后重试");
    }
}
