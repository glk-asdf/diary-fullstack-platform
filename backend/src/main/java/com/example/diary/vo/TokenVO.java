package com.example.diary.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录 / 刷新令牌的返回结果。
 */
@Schema(description = "登录令牌")
public record TokenVO(
        @Schema(description = "访问令牌，放入 Authorization: Bearer 头") String accessToken,
        @Schema(description = "刷新令牌，用于 accessToken 过期后换新") String refreshToken,
        @Schema(description = "accessToken 有效期（秒）", example = "1800") long expiresIn,
        @Schema(description = "当前用户信息") UserVO user) {
}
