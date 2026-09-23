package com.example.diary.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 附件实体，对应表 {@code t_attachment}。
 * <p>上传时 {@code diaryId} 为空（此时日记可能尚未保存），绑定关系由 Markdown 中的 URL 承载。</p>
 */
@TableName("t_attachment")
public class Attachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long diaryId;

    private Long userId;

    private String url;

    private String filename;

    private Long size;

    private String mimeType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public Long getDiaryId() { return diaryId; }

    public void setDiaryId(Long diaryId) { this.diaryId = diaryId; }

    public Long getUserId() { return userId; }

    public void setUserId(Long userId) { this.userId = userId; }

    public String getUrl() { return url; }

    public void setUrl(String url) { this.url = url; }

    public String getFilename() { return filename; }

    public void setFilename(String filename) { this.filename = filename; }

    public Long getSize() { return size; }

    public void setSize(Long size) { this.size = size; }

    public String getMimeType() { return mimeType; }

    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
