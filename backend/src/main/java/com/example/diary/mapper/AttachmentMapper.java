package com.example.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.diary.entity.Attachment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 附件数据访问。
 */
@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
}
