package com.example.diary.service;

import com.example.diary.common.exception.BizException;
import com.example.diary.common.result.PageResult;
import com.example.diary.dto.DiaryQueryDTO;
import com.example.diary.dto.DiarySaveDTO;
import com.example.diary.entity.Tag;
import com.example.diary.mapper.TagMapper;
import com.example.diary.security.LoginUser;
import com.example.diary.vo.DiaryDetailVO;
import com.example.diary.vo.DiaryVO;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3 核心验收：日记 CRUD、摘要生成、列表筛选、标签关联与数据隔离。
 */
@SpringBootTest
@Transactional
class DiaryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 999L;

    @Autowired
    private DiaryService diaryService;

    @Autowired
    private TagMapper tagMapper;

    @BeforeEach
    void setUp() {
        login(USER_ID, "tester");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("创建：摘要自动去除 Markdown 标记")
    void createGeneratesPlainSummary() {
        DiaryDetailVO created = diaryService.create(new DiarySaveDTO(
                "周末爬山",
                "# 今天\n\n天气很好，和朋友去**爬山**了。\n\n```java\nSystem.out.println(\"hi\");\n```\n\n![图](https://example.com/a.jpg)",
                1, "晴", LocalDate.of(2026, 9, 20), List.of(), 0));

        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo("周末爬山");
        assertThat(created.mood()).isEqualTo(1);
        assertThat(created.diaryDate()).isEqualTo(LocalDate.of(2026, 9, 20));

        String summary = created.summary();
        assertThat(summary).isNotNull();
        assertThat(summary).doesNotContain("#", "**", "```", "System.out", "![");
        assertThat(summary).contains("天气很好").contains("爬山");
        assertThat(created.tags()).isEmpty();
    }

    @Test
    @DisplayName("更新与删除：更新生效，逻辑删除后详情查不到")
    void updateAndLogicalDelete() {
        DiaryDetailVO created = diaryService.create(quickSave("原标题", "原内容"));

        DiaryDetailVO updated = diaryService.update(created.id(),
                new DiarySaveDTO("改后的标题", "改后的内容", 3, "雨", LocalDate.of(2026, 1, 1), List.of(), 1));

        assertThat(updated.title()).isEqualTo("改后的标题");
        assertThat(updated.content()).isEqualTo("改后的内容");
        assertThat(updated.mood()).isEqualTo(3);
        assertThat(updated.isPublic()).isEqualTo(1);

        diaryService.delete(created.id());

        assertThatThrownBy(() -> diaryService.detail(created.id()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("日记不存在");
    }

    @Test
    @DisplayName("列表：分页与关键词 / 心情 / 日期区间筛选生效，size 越界被限制")
    void listFilters() {
        diaryService.create(quickSave("清晨跑步", "沿着河边跑了五公里", LocalDate.of(2026, 3, 1), 1));
        diaryService.create(quickSave("雨天读书", "窝在沙发里看了一下午书", LocalDate.of(2026, 6, 15), 2));
        diaryService.create(quickSave("深夜加班", "需求又改了", LocalDate.of(2026, 9, 20), 4));

        // 无条件分页
        PageResult<DiaryVO> all = diaryService.page(new DiaryQueryDTO(null, null, null, null, null, null, null));
        assertThat(all.total()).isGreaterThanOrEqualTo(3);
        assertThat(all.current()).isEqualTo(1);
        assertThat(all.records()).isNotEmpty();
        assertThat(all.records()).allMatch(vo -> vo.id() != null);

        // 关键词命中标题或摘要
        PageResult<DiaryVO> byKeyword = diaryService.page(
                new DiaryQueryDTO(null, null, "跑步", null, null, null, null));
        assertThat(byKeyword.records()).extracting(DiaryVO::title).contains("清晨跑步");

        // 心情
        PageResult<DiaryVO> byMood = diaryService.page(
                new DiaryQueryDTO(null, null, null, null, 4, null, null));
        assertThat(byMood.records()).isNotEmpty();
        assertThat(byMood.records()).allMatch(vo -> vo.mood() == 4);

        // 日期区间（含边界）
        PageResult<DiaryVO> byDate = diaryService.page(
                new DiaryQueryDTO(null, null, null, null, null, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 9, 20)));
        assertThat(byDate.records()).extracting(DiaryVO::title)
                .contains("雨天读书", "深夜加班")
                .doesNotContain("清晨跑步");

        // 排序：diary_date 倒序
        assertThat(byDate.records().get(0).diaryDate()).isEqualTo(LocalDate.of(2026, 9, 20));

        // size 越界被限制为 50
        PageResult<DiaryVO> oversized = diaryService.page(
                new DiaryQueryDTO(1, 500, null, null, null, null, null));
        assertThat(oversized.size()).isEqualTo(50);
    }

    @Test
    @DisplayName("标签：可关联自有标签、可按标签筛选，不能关联他人标签")
    void tagAssociation() {
        Tag tag = new Tag();
        tag.setUserId(USER_ID);
        tag.setName("旅行");
        tag.setColor("#52c41a");
        tagMapper.insert(tag);

        DiaryDetailVO withTag = diaryService.create(new DiarySaveDTO(
                "带标签的日记", "去了趟山里", 2, "多云", LocalDate.of(2026, 5, 1), List.of(tag.getId()), 0));

        assertThat(withTag.tags()).hasSize(1);
        assertThat(withTag.tags().get(0).name()).isEqualTo("旅行");
        assertThat(withTag.tags().get(0).color()).isEqualTo("#52c41a");

        PageResult<DiaryVO> byTag = diaryService.page(
                new DiaryQueryDTO(1, 10, null, tag.getId(), null, null, null));
        assertThat(byTag.records()).extracting(DiaryVO::title).contains("带标签的日记");
        assertThat(byTag.records().get(0).tags()).hasSize(1);

        // 换用户后无法关联他人标签
        login(OTHER_USER_ID, "other");
        assertThatThrownBy(() -> diaryService.create(new DiarySaveDTO(
                "越权尝试", "x", 1, null, LocalDate.now(), List.of(tag.getId()), 0)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签");
    }

    @Test
    @DisplayName("数据隔离：访问他人日记一律按不存在处理")
    void cannotAccessOthersDiary() {
        DiaryDetailVO created = diaryService.create(quickSave("我的日记", "只有我能看"));

        login(OTHER_USER_ID, "other");

        assertThatThrownBy(() -> diaryService.detail(created.id()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("日记不存在");
        assertThatThrownBy(() -> diaryService.update(created.id(), quickSave("篡改", "篡改")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("日记不存在");
        assertThatThrownBy(() -> diaryService.delete(created.id()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("日记不存在");

        // 他人列表里不应出现这篇日记
        PageResult<DiaryVO> othersList = diaryService.page(new DiaryQueryDTO(null, null, null, null, null, null, null));
        assertThat(othersList.records()).extracting(DiaryVO::title).doesNotContain("我的日记");
    }

    private DiarySaveDTO quickSave(String title, String content) {
        return new DiarySaveDTO(title, content, 1, "晴", LocalDate.now(), List.of(), 0);
    }

    private DiarySaveDTO quickSave(String title, String content, LocalDate date, Integer mood) {
        return new DiarySaveDTO(title, content, mood, "晴", date, List.of(), 0);
    }

    private void login(Long userId, String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new LoginUser(userId, username), null, Collections.emptyList()));
    }
}
