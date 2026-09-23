package com.example.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.diary.entity.Diary;
import com.example.diary.vo.DayCountVO;
import com.example.diary.vo.MoodCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 日记数据访问。
 * <p>简单 CRUD 复用 {@link BaseMapper}；统计类聚合查询用注解 SQL 手写，
 * 走 {@code idx_user_date} / {@code idx_user_mood} 索引。</p>
 */
@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {

    /** 指定日期区间内每天的篇数，用于写作热力图（仅返回有记录的日期） */
    @Select("""
            SELECT diary_date AS diaryDate, COUNT(*) AS total
            FROM t_diary
            WHERE user_id = #{userId} AND deleted = 0
              AND diary_date BETWEEN #{start} AND #{end}
            GROUP BY diary_date
            ORDER BY diary_date
            """)
    List<DayCountVO> countByDateRange(@Param("userId") Long userId,
                                      @Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    /** 该用户全部已写日记的日期（升序去重），连续天数计算依赖它 */
    @Select("""
            SELECT DISTINCT diary_date
            FROM t_diary
            WHERE user_id = #{userId} AND deleted = 0
            ORDER BY diary_date
            """)
    List<LocalDate> listDistinctDates(@Param("userId") Long userId);

    /**
     * 心情分布。用 {@code IFNULL(mood, 0)} 把未记录心情的日记归入 0，
     * 从而保证各段之和恒等于总篇数（见 P7 验收标准第 2 条）。
     */
    @Select("""
            SELECT IFNULL(mood, 0) AS mood, COUNT(*) AS total
            FROM t_diary
            WHERE user_id = #{userId} AND deleted = 0
            GROUP BY IFNULL(mood, 0)
            ORDER BY mood
            """)
    List<MoodCountVO> countByMood(@Param("userId") Long userId);
}
