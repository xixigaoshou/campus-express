package com.campus.express.common;

/**
 * 业务异常：业务规则校验失败时抛出，由全局异常处理器统一转成 Result
 */
public class BusinessException extends RuntimeException {

    /** 错误码，默认 400（参数或业务错误） */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
