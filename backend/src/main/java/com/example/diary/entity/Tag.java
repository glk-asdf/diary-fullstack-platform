package com.example.diary.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 标签实体，对应表 {@code t_tag}。
 */
@TableName("t_tag")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String color;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }

    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getColor() { return color; }

    public void setColor(String color) { this.color = color; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
