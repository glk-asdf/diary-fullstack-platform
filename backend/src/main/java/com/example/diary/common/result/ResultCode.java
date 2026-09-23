package com.example.diary.common.result;

/**
 * 业务响应码。
 */
public enum ResultCode {

    SUCCESS(0, "ok"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证或登录已失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器开小差了");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
