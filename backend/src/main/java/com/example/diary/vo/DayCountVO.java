package com.example.diary.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 某一天的日记篇数，用于写作热力图。
 * <p>仅返回有记录的日期，无记录的格子由前端补空。</p>
 */
@Schema(description = "单日日记篇数")
public record DayCountVO(
        @Schema(description = "日期", example = "2026-09-20") LocalDate diaryDate,
        @Schema(description = "当日篇数", example = "2") long total) {
}
