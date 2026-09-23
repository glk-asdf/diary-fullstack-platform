package com.example.diary.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 日记列表项。
 * <p>刻意不包含 {@code content}：列表页不读取 LONGTEXT，避免拖慢查询。</p>
 */
@Schema(description = "日记列表项")
public record DiaryVO(
        @Schema(description = "日记 ID", example = "1") Long id,
        @Schema(description = "标题", example = "周末爬山") String title,
        @Schema(description = "摘要，由正文去除 Markdown 标记后截断生成") String summary,
        @Schema(description = "心情：1开心 2平静 3难过 4焦虑 5生气", example = "1") Integer mood,
        @Schema(description = "天气", example = "晴") String weather,
        @Schema(description = "日记归属日期", example = "2026-09-20") LocalDate diaryDate,
        @Schema(description = "是否公开：0否 1是", example = "0") Integer isPublic,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt,
        @Schema(description = "标签列表") List<TagVO> tags) {
}
