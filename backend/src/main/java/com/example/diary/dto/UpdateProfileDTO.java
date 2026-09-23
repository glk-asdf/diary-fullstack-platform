package com.example.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(description = "修改个人资料请求（字段为 null 表示不修改）")
public record UpdateProfileDTO(

        @Schema(description = "昵称", example = "新昵称")
        @Size(max = 20, message = "昵称最长 20 个字符")
        String nickname,

        @Schema(description = "头像地址")
        @Size(max = 255, message = "头像地址最长 255 个字符")
        String avatar,

        @Schema(description = "邮箱", example = "new@example.com")
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱最长 100 个字符")
        String email) {
}
