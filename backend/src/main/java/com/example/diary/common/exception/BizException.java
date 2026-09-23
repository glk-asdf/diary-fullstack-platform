package com.example.diary.common.exception;

import com.example.diary.common.result.ResultCode;

/**
 * 业务异常。由 {@link GlobalExceptionHandler} 统一转换为 {@code Result}。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
