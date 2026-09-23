package com.example.diary.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日记实体，对应表 {@code t_diary}。
 * <p>项目不使用 Lombok，访问器显式书写。</p>
 */
@TableName("t_diary")
public class Diary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /** Markdown 原文，列表查询禁止读取该字段 */
    private String content;

    /** 列表页摘要，冗余存储 */
    private String summary;

    /** 1开心 2平静 3难过 4焦虑 5生气 */
    private Integer mood;

    private String weather;

    /** 日记归属日期，可与创建时间不同，支持补写 */
    private LocalDate diaryDate;

    private Integer isPublic;

    /** 逻辑删除标记，显式初始化为 0，避免插入时为 null 而依赖数据库默认值 */
    @TableLogic
    private Integer deleted = 0;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }

    public void setUserId(Long userId) { this.userId = userId; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public String getSummary() { return summary; }

    public void setSummary(String summary) { this.summary = summary; }

    public Integer getMood() { return mood; }

    public void setMood(Integer mood) { this.mood = mood; }

    public String getWeather() { return weather; }

    public void setWeather(String weather) { this.weather = weather; }

    public LocalDate getDiaryDate() { return diaryDate; }

    public void setDiaryDate(LocalDate diaryDate) { this.diaryDate = diaryDate; }

    public Integer getIsPublic() { return isPublic; }

    public void setIsPublic(Integer isPublic) { this.isPublic = isPublic; }

    public Integer getDeleted() { return deleted; }

    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "Diary{id=" + id + ", userId=" + userId + ", title='" + title + "', diaryDate=" + diaryDate
                + ", mood=" + mood + ", deleted=" + deleted + '}';
    }
}
