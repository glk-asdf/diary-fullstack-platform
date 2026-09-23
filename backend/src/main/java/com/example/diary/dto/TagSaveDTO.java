package com.example.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "新建或修改标签")
public record TagSaveDTO(

        @Schema(description = "标签名", example = "旅行")
        @NotBlank(message = "标签名不能为空")
        @Size(max = 30, message = "标签名最长 30 个字符")
        String name,

        @Schema(description = "标签颜色，留空则使用默认色", example = "#52c41a")
        @Size(max = 10, message = "颜色值过长")
        String color) {
}
