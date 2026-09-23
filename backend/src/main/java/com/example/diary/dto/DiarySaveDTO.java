package com.example.diary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 新建 / 更新日记的入参。
 * <p>两者字段完全一致（都是全量提交），故合并为一个 DTO 而非拆成 Create/Update 两份。</p>
 */
@Schema(description = "新建或更新日记")
public record DiarySaveDTO(

        @Schema(description = "标题", example = "周末爬山")
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题最长 200 个字符")
        String title,

        @Schema(description = "Markdown 正文")
        @Size(max = 100_000, message = "正文过长")
        String content,

        @Schema(description = "心情：1开心 2平静 3难过 4焦虑 5生气", example = "1")
        @Min(value = 1, message = "心情取值 1-5")
        @Max(value = 5, message = "心情取值 1-5")
        Integer mood,

        @Schema(description = "天气", example = "晴")
        @Size(max = 20, message = "天气最长 20 个字符")
        String weather,

        @Schema(description = "日记归属日期，可补写历史日期", example = "2026-09-20")
        @NotNull(message = "日记日期不能为空")
        LocalDate diaryDate,

        @Schema(description = "标签 ID 列表")
        List<Long> tagIds,

        @Schema(description = "是否公开：0否 1是", example = "0")
        Integer isPublic) {
}
