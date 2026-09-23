package com.example.diary.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.diary.common.exception.BizException;
import com.example.diary.common.result.ResultCode;
import com.example.diary.common.util.SecurityUtil;
import com.example.diary.entity.Diary;
import com.example.diary.mapper.DiaryMapper;
import com.example.diary.service.StatsService;
import com.example.diary.vo.DayCountVO;
import com.example.diary.vo.MoodCountVO;
import com.example.diary.vo.StatsOverviewVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StatsServiceImpl implements StatsService {

    private static final int MIN_YEAR = 1970;
    private static final int MAX_YEAR = 2100;

    private final DiaryMapper diaryMapper;

    public StatsServiceImpl(DiaryMapper diaryMapper) {
        this.diaryMapper = diaryMapper;
    }

    @Override
    public List<DayCountVO> calendar(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BizException(ResultCode.BAD_REQUEST, "年份超出可统计范围");
        }

        Long userId = SecurityUtil.getCurrentUserId();
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        return diaryMapper.countByDateRange(userId, start, end);
    }

    @Override
    public StatsOverviewVO overview() {
        Long userId = SecurityUtil.getCurrentUserId();
        LocalDate today = LocalDate.now();

        Long totalCount = diaryMapper.selectCount(
                Wrappers.<Diary>lambdaQuery().eq(Diary::getUserId, userId));
        Long monthCount = diaryMapper.selectCount(
                Wrappers.<Diary>lambdaQuery()
                        .eq(Diary::getUserId, userId)
                        .ge(Diary::getDiaryDate, today.withDayOfMonth(1))
                        .le(Diary::getDiaryDate, today.withDayOfMonth(today.lengthOfMonth())));

        // 一次取出全部写作日期，供首末日期与两个连续天数指标复用
        List<LocalDate> dates = diaryMapper.listDistinctDates(userId);
        List<MoodCountVO> moodDistribution = diaryMapper.countByMood(userId);

        return new StatsOverviewVO(
                totalCount == null ? 0 : totalCount,
                monthCount == null ? 0 : monthCount,
                currentStreak(dates, today),
                longestStreak(dates),
                dates.isEmpty() ? null : dates.get(0),
                dates.isEmpty() ? null : dates.get(dates.size() - 1),
                moodDistribution);
    }

    /**
     * 当前连续打卡天数。
     * <p>产品定义：以「今天」为基准向前连续计数；若今天尚未写而昨天写了，
     * 视为连续未中断（今天还没结束），从昨天起算；若最近一篇早于昨天，则为 0。</p>
     */
    private int currentStreak(List<LocalDate> ascendingDates, LocalDate today) {
        if (ascendingDates.isEmpty()) {
            return 0;
        }

        LocalDate latest = ascendingDates.get(ascendingDates.size() - 1);
        LocalDate expected;
        if (latest.equals(today)) {
            expected = today;
        } else if (latest.equals(today.minusDays(1))) {
            expected = today.minusDays(1);
        } else {
            return 0;
        }

        int streak = 0;
        for (int i = ascendingDates.size() - 1; i >= 0; i--) {
            if (ascendingDates.get(i).equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    /** 历史最长连续天数，跨月、跨年由 {@link LocalDate#plusDays} 自然处理 */
    private int longestStreak(List<LocalDate> ascendingDates) {
        int longest = 0;
        int current = 0;
        LocalDate previous = null;

        for (LocalDate date : ascendingDates) {
            current = (previous != null && date.equals(previous.plusDays(1))) ? current + 1 : 1;
            longest = Math.max(longest, current);
            previous = date;
        }
        return longest;
    }
}
