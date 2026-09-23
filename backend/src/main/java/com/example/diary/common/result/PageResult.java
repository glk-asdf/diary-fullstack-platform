package com.example.diary.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 分页结果。
 *
 * @param total   总记录数
 * @param pages   总页数
 * @param current 当前页码
 * @param size    每页条数
 * @param records 当前页数据
 */
@Schema(description = "分页结果")
public record PageResult<T>(
        @Schema(description = "总记录数", example = "128") long total,
        @Schema(description = "总页数", example = "13") long pages,
        @Schema(description = "当前页码", example = "1") long current,
        @Schema(description = "每页条数", example = "10") long size,
        @Schema(description = "当前页数据") List<T> records) {

    /** 由 MyBatis-Plus 分页对象转换 */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /** 由已转换好的 VO 列表与原始分页信息组合 */
    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }
}
