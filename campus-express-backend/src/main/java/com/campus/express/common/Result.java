package com.campus.express.common;

import lombok.Data;

/**
 * 统一返回结果包装（REST 接口规范）
 * 结构：{ code, message, data }
 */
@Data
public class Result<T> {

    /** 状态码：200 成功 / 401 未登录 / 403 无权限 / 500 服务器错误 / 400 参数或业务错误 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 业务数据 */
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = message;
        r.data = data;
        return r;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> Result<T> error(String message) {
        return error(400, message);
    }

    /** 未登录 / token 失效 */
    public static <T> Result<T> unauthorized(String message) {
        return error(401, message);
    }

    /** 无权限操作（敏感操作鉴权失败） */
    public static <T> Result<T> forbidden(String message) {
        return error(403, message);
    }
}
