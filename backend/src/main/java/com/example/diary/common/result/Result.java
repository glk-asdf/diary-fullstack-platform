package com.example.diary.common.result;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一响应体。
 * <p>HTTP 状态码保持语义正确，业务码放在 {@code code} 字段中。</p>
 *
 * @param code    业务码，0 表示成功
 * @param message 提示信息
 * @param data    业务数据
 */
@Schema(description = "统一响应体")
public record Result<T>(
        @Schema(description = "业务码，0 表示成功", example = "0") int code,
        @Schema(description = "提示信息", example = "ok") String message,
        @Schema(description = "业务数据") T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }
}
