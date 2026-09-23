package com.example.diary.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文件上传结果")
public record FileVO(
        @Schema(description = "附件 ID", example = "1") Long id,
        @Schema(description = "可直接访问的图片地址") String url,
        @Schema(description = "原始文件名", example = "photo.jpg") String filename,
        @Schema(description = "文件大小（字节）", example = "102400") long size,
        @Schema(description = "MIME 类型", example = "image/jpeg") String mimeType) {
}
