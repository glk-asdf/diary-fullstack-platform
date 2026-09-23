package com.example.diary.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户信息出参。刻意不包含 password 字段。
 */
@Schema(description = "用户信息")
public record UserVO(
        @Schema(description = "用户 ID", example = "1") Long id,
        @Schema(description = "登录名", example = "tester") String username,
        @Schema(description = "昵称", example = "测试用户") String nickname,
        @Schema(description = "头像地址") String avatar,
        @Schema(description = "邮箱") String email) {
}
