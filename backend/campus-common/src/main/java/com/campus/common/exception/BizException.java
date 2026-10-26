package com.campus.common.exception;

import lombok.Getter;

/**
 * 业务异常，由全局异常处理器转成统一响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of(String message) {
        return new BizException(message);
    }

    public static BizException unauthorized() {
        return new BizException(401, "请先登录");
    }

    /** 需要给前端说明具体原因时用（例如「登录状态已失效」与「账号已禁用」要区分） */
    public static BizException unauthorized(String msg) {
        return new BizException(401, msg);
    }

    public static BizException forbidden(String msg) {
        return new BizException(403, msg);
    }

    public static BizException notFound(String msg) {
        return new BizException(404, msg);
    }

    /** 触发频次限制（登录爆破、刷接口）。前端按「稍后再试」处理。 */
    public static BizException tooManyRequests(String msg) {
        return new BizException(429, msg);
    }
}
