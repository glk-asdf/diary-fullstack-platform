package com.example.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 日记-标签关联实体，对应表 {@code t_diary_tag}。
 * <p>表为复合主键（diary_id, tag_id），此处把 diary_id 声明为主键以便复用
 * {@code deleteById} 批量清除某篇日记的全部标签关联；tag_id 作为普通字段参与查询条件。</p>
 */
@TableName("t_diary_tag")
public class DiaryTag {

    @TableId(value = "diary_id", type = IdType.INPUT)
    private Long diaryId;

    private Long tagId;

    public DiaryTag() {
    }

    public DiaryTag(Long diaryId, Long tagId) {
        this.diaryId = diaryId;
        this.tagId = tagId;
    }

    public Long getDiaryId() { return diaryId; }

    public void setDiaryId(Long diaryId) { this.diaryId = diaryId; }

    public Long getTagId() { return tagId; }

    public void setTagId(Long tagId) { this.tagId = tagId; }
}
