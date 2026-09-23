package com.example.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "注册请求")
public record RegisterDTO(

        @Schema(description = "登录名", example = "tester")
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名需为 4-20 位字母、数字或下划线")
        String username,

        @Schema(description = "密码", example = "123456")
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
        String password,

        @Schema(description = "昵称", example = "测试用户")
        @Size(max = 20, message = "昵称最长 20 个字符")
        String nickname,

        @Schema(description = "邮箱", example = "tester@example.com")
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱最长 100 个字符")
        String email) {
}
