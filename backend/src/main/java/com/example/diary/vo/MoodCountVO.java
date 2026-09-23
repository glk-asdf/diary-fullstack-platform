package com.example.diary.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 心情分布中的一段。
 * <p>{@code mood = 0} 表示未记录心情的日记，保证各段之和恒等于总篇数。</p>
 */
@Schema(description = "心情分布项")
public record MoodCountVO(
        @Schema(description = "心情编码，0 表示未记录", example = "1") Integer mood,
        @Schema(description = "该心情的篇数", example = "12") long total) {
}
