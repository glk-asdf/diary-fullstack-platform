package com.example.diary.mapper;

import com.example.diary.entity.Diary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 P1 的三项基础设施能力：字段自动填充、逻辑删除、以及 BaseMapper 基本可用性。
 * <p>测试结束自动回滚，不污染数据库。</p>
 */
@SpringBootTest
@Transactional
class DiaryMapperTest {

    @Autowired
    private DiaryMapper diaryMapper;

    @Test
    @DisplayName("插入自动填充时间字段，查询正常，逻辑删除后查不到")
    void insertFillAndLogicDelete() {
        Diary diary = new Diary();
        diary.setUserId(1L);
        diary.setTitle("P1 验证：自动填充与逻辑删除");
        diary.setContent("# 正文\n\n用于验证的时间字段自动填充。");
        diary.setSummary("P1 验证");
        diary.setMood(1);
        diary.setWeather("晴");
        diary.setDiaryDate(LocalDate.now());
        diary.setIsPublic(0);

        assertThat(diaryMapper.insert(diary)).isEqualTo(1);
        assertThat(diary.getId()).isNotNull();

        // 自动填充
        assertThat(diary.getCreatedAt()).isNotNull();
        assertThat(diary.getUpdatedAt()).isNotNull();
        assertThat(diary.getDeleted()).isEqualTo(0);

        // 常规查询
        Diary loaded = diaryMapper.selectById(diary.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getUserId()).isEqualTo(1L);
        assertThat(loaded.getTitle()).isEqualTo("P1 验证：自动填充与逻辑删除");
        assertThat(loaded.getDiaryDate()).isEqualTo(LocalDate.now());

        // 逻辑删除：deleteById 翻译为 UPDATE ... SET deleted = 1
        assertThat(diaryMapper.deleteById(diary.getId())).isEqualTo(1);
        assertThat(diaryMapper.selectById(diary.getId())).isNull();
    }
}
