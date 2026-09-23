package com.example.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "刷新令牌请求")
public record RefreshTokenDTO(

        @Schema(description = "登录时下发的 refreshToken")
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken) {
}
