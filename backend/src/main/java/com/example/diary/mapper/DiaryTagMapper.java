package com.example.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.diary.entity.DiaryTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日记-标签关联数据访问。
 */
@Mapper
public interface DiaryTagMapper extends BaseMapper<DiaryTag> {
}
