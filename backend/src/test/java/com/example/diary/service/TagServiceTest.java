package com.example.diary.service;

import com.example.diary.common.exception.BizException;
import com.example.diary.dto.DiarySaveDTO;
import com.example.diary.dto.TagSaveDTO;
import com.example.diary.security.LoginUser;
import com.example.diary.vo.DiaryDetailVO;
import com.example.diary.vo.TagVO;
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
 * P4 核心验收：标签 CRUD、重名校验、删除时解除关联、跨用户隔离。
 */
@SpringBootTest
@Transactional
class TagServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 999L;

    @Autowired
    private TagService tagService;

    @Autowired
    private DiaryService diaryService;

    @BeforeEach
    void setUp() {
        login(USER_ID, "tester");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("标签 CRUD：重名校验、默认色、删除时同步解除日记关联")
    void tagCrud() {
        TagVO created = tagService.create(new TagSaveDTO("旅行", "#52c41a"));
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("旅行");
        assertThat(created.color()).isEqualTo("#52c41a");

        // 同名标签被拒绝
        assertThatThrownBy(() -> tagService.create(new TagSaveDTO("旅行", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");

        // 不传颜色时使用默认色
        TagVO defaultColor = tagService.create(new TagSaveDTO("读书", null));
        assertThat(defaultColor.color()).isEqualTo("#1677ff");

        assertThat(tagService.list()).extracting(TagVO::name).contains("旅行", "读书");

        // 修改
        TagVO updated = tagService.update(created.id(), new TagSaveDTO("旅行日记", "#faad14"));
        assertThat(updated.name()).isEqualTo("旅行日记");
        assertThat(updated.color()).isEqualTo("#faad14");

        // 改名撞上已有标签
        assertThatThrownBy(() -> tagService.update(created.id(), new TagSaveDTO("读书", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");

        // 关联到日记后删除标签，日记详情里的标签应同步消失
        DiaryDetailVO diary = diaryService.create(new DiarySaveDTO(
                "带标签的日记", "内容", 1, "晴", LocalDate.now(), List.of(created.id()), 0));
        assertThat(diary.tags()).hasSize(1);

        tagService.delete(created.id());

        assertThat(tagService.list()).extracting(TagVO::name).doesNotContain("旅行日记");
        assertThat(diaryService.detail(diary.id()).tags()).isEmpty();
    }

    @Test
    @DisplayName("隔离：不同用户可同名，且不能查看或操作他人标签")
    void isolation() {
        TagVO mine = tagService.create(new TagSaveDTO("旅行", null));

        login(OTHER_USER_ID, "other");

        // 不同用户下同名标签可共存
        TagVO others = tagService.create(new TagSaveDTO("旅行", null));
        assertThat(others.id()).isNotEqualTo(mine.id());

        // 他人标签不出现在自己的列表里
        assertThat(tagService.list()).extracting(TagVO::id).containsExactly(others.id());

        // 不能修改 / 删除他人标签，统一按不存在处理
        assertThatThrownBy(() -> tagService.update(mine.id(), new TagSaveDTO("改个名", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签不存在");
        assertThatThrownBy(() -> tagService.delete(mine.id()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("标签不存在");
    }

    private void login(Long userId, String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new LoginUser(userId, username), null, Collections.emptyList()));
    }
}
