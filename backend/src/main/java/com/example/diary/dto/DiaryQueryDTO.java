package com.example.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 日记列表查询条件。
 * <p>由 Spring MVC 的构造器绑定从 query 参数填充，字段全部可选。</p>
 */
@Schema(description = "日记列表查询条件")
public record DiaryQueryDTO(

        @Schema(description = "页码，从 1 开始", example = "1")
        Integer page,

        @Schema(description = "每页条数，最大 50", example = "10")
        Integer size,

        @Schema(description = "关键词，匹配标题与摘要", example = "旅行")
        String keyword,

        @Schema(description = "标签 ID", example = "3")
        Long tagId,

        @Schema(description = "心情：1开心 2平静 3难过 4焦虑 5生气", example = "1")
        Integer mood,

        @Schema(description = "起始日期（含）", example = "2026-01-01")
        LocalDate startDate,

        @Schema(description = "结束日期（含）", example = "2026-09-23")
        LocalDate endDate) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;

    public long pageOrDefault() {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    public long sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
