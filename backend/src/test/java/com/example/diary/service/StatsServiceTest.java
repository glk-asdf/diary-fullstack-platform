package com.example.diary.service;

import com.example.diary.common.exception.BizException;
import com.example.diary.dto.DiarySaveDTO;
import com.example.diary.security.LoginUser;
import com.example.diary.vo.DayCountVO;
import com.example.diary.vo.MoodCountVO;
import com.example.diary.vo.StatsOverviewVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P7 核心验收：热力图日期对应、心情分布求和、连续天数边界、数据隔离。
 *
 * <p>统计断言的是绝对数值，而开发过程中 tester（id=1）名下可能已有真实日记，
 * 因此本类刻意使用与真实账号无关的用户 ID，让测试与线上数据彻底隔离。</p>
 */
@SpringBootTest
@Transactional
class StatsServiceTest {

    private static final Long USER_ID = 90001L;
    private static final Long OTHER_USER_ID = 90002L;

    @Autowired
    private StatsService statsService;

    @Autowired
    private DiaryService diaryService;

    @BeforeEach
    void setUp() {
        login(USER_ID, "stats-tester");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("当前连续：逐日补齐时增长，中间断档则不增长")
    void currentStreakGrowsWithConsecutiveDays() {
        LocalDate today = LocalDate.now();

        assertThat(statsService.overview().currentStreak()).isZero();

        // 只写今天 → 1
        create(today, 1);
        assertThat(statsService.overview().currentStreak()).isEqualTo(1);

        // 补昨天 → 2
        create(today.minusDays(1), 1);
        assertThat(statsService.overview().currentStreak()).isEqualTo(2);

        // 跳过前天、直接补大前天 → 链在前天处断开，仍为 2
        create(today.minusDays(3), 1);
        assertThat(statsService.overview().currentStreak()).isEqualTo(2);

        // 补上缺失的前天 → t-3 / t-2 / t-1 / t 连成一片，共 4 天
        create(today.minusDays(2), 1);
        assertThat(statsService.overview().currentStreak()).isEqualTo(4);
    }

    @Test
    @DisplayName("当前连续：今天尚未写但昨天写了，视为延续（今天还没结束）")
    void currentStreakContinuesWhenTodayNotWritten() {
        // 独立用户，避免与本类其他用例的数据互相干扰
        login(90003L, "stats-tester-2");
        LocalDate today = LocalDate.now();

        create(today.minusDays(1), 1);
        create(today.minusDays(2), 1);

        assertThat(statsService.overview().currentStreak()).isEqualTo(2);
    }

    @Test
    @DisplayName("当前连续：最近一篇早于昨天则归零")
    void currentStreakResetsWhenLatestIsTooOld() {
        login(90004L, "stats-tester-3");
        LocalDate today = LocalDate.now();

        create(today.minusDays(2), 1);
        create(today.minusDays(3), 1);

        assertThat(statsService.overview().currentStreak()).isZero();
    }

    @Test
    @DisplayName("最长连续：同一天多篇只算一天，跨月边界正确")
    void longestStreakAcrossMonths() {
        LocalDate start = LocalDate.of(2026, 1, 30);
        create(start, 1);
        create(start, 2);              // 同一天第二篇，不应额外增加天数
        create(start.plusDays(1), 1);  // 1/31
        create(start.plusDays(2), 1);  // 2/1 跨月
        create(start.plusDays(3), 1);  // 2/2
        create(start.plusDays(10), 1); // 断开

        StatsOverviewVO overview = statsService.overview();
        assertThat(overview.longestStreak()).isEqualTo(4);
        assertThat(overview.totalCount()).isEqualTo(6);
        assertThat(overview.firstDate()).isEqualTo(start);
        assertThat(overview.lastDate()).isEqualTo(start.plusDays(10));
    }

    @Test
    @DisplayName("热力图：日期与 diary_date 完全对应，跨年不串，年份越界被拒")
    void calendarMapping() {
        create(LocalDate.of(2026, 3, 15), 1);
        create(LocalDate.of(2026, 3, 15), 2);
        create(LocalDate.of(2026, 5, 1), 3);
        create(LocalDate.of(2025, 3, 15), 1);

        Map<LocalDate, Long> days2026 = statsService.calendar(2026).stream()
                .collect(Collectors.toMap(DayCountVO::diaryDate, DayCountVO::total));

        assertThat(days2026).containsEntry(LocalDate.of(2026, 3, 15), 2L);
        assertThat(days2026).containsEntry(LocalDate.of(2026, 5, 1), 1L);
        assertThat(days2026).doesNotContainKey(LocalDate.of(2025, 3, 15));

        Map<LocalDate, Long> days2025 = statsService.calendar(2025).stream()
                .collect(Collectors.toMap(DayCountVO::diaryDate, DayCountVO::total));
        assertThat(days2025).containsOnlyKeys(LocalDate.of(2025, 3, 15));

        assertThatThrownBy(() -> statsService.calendar(1900))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("年份");
    }

    @Test
    @DisplayName("心情分布各段之和等于总篇数，未记录心情归入 0")
    void moodDistributionSumsToTotal() {
        LocalDate today = LocalDate.now();
        create(today, 1);
        create(today, 1);
        create(today, 3);
        create(today, null);

        StatsOverviewVO overview = statsService.overview();

        assertThat(overview.totalCount()).isEqualTo(4);
        assertThat(overview.moodDistribution().stream().mapToLong(MoodCountVO::total).sum())
                .isEqualTo(overview.totalCount());

        Map<Integer, Long> byMood = overview.moodDistribution().stream()
                .collect(Collectors.toMap(MoodCountVO::mood, MoodCountVO::total));
        assertThat(byMood).containsEntry(1, 2L).containsEntry(3, 1L).containsEntry(0, 1L);
    }

    @Test
    @DisplayName("数据隔离：只统计当前登录用户，且不含逻辑删除的记录")
    void isolationAndLogicalDelete() {
        LocalDate today = LocalDate.now();
        create(today, 1);
        Long deletedId = create(today.minusDays(1), 1);

        assertThat(statsService.overview().totalCount()).isEqualTo(2);

        diaryService.delete(deletedId);
        assertThat(statsService.overview().totalCount()).isEqualTo(1);
        assertThat(statsService.overview().longestStreak()).isEqualTo(1);

        login(OTHER_USER_ID, "other");
        assertThat(statsService.overview().totalCount()).isZero();
        assertThat(statsService.overview().moodDistribution()).isEmpty();
        assertThat(statsService.calendar(today.getYear())).isEmpty();
    }

    private Long create(LocalDate date, Integer mood) {
        return diaryService.create(new DiarySaveDTO(
                "统计测试 " + date, "内容", mood, "晴", date, List.of(), 0)).id();
    }

    private void login(Long userId, String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new LoginUser(userId, username), null, Collections.emptyList()));
    }
}
