package com.example.diary.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "统计概览")
public record StatsOverviewVO(
        @Schema(description = "总篇数", example = "86") long totalCount,
        @Schema(description = "本月篇数", example = "7") long monthCount,
        @Schema(description = "当前连续打卡天数", example = "5") int currentStreak,
        @Schema(description = "历史最长连续打卡天数", example = "21") int longestStreak,
        @Schema(description = "最早一篇的日期") LocalDate firstDate,
        @Schema(description = "最近一篇的日期") LocalDate lastDate,
        @Schema(description = "心情分布，各段之和等于总篇数") List<MoodCountVO> moodDistribution) {
}
