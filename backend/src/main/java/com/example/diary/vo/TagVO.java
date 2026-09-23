package com.example.diary.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "标签")
public record TagVO(
        @Schema(description = "标签 ID", example = "3") Long id,
        @Schema(description = "标签名", example = "旅行") String name,
        @Schema(description = "标签颜色", example = "#52c41a") String color) {
}
