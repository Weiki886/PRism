package com.weiki.prismbackend.common;

public enum ResultCode {
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 已过期"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    USERNAME_EXISTS(409, "用户名已存在"),
    EMAIL_EXISTS(409, "邮箱已被注册"),
    WRONG_PASSWORD(400, "密码错误"),
    REVIEW_IN_PROGRESS(409, "该 PR 正在分析中，请稍后再试"),
    SERVER_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
