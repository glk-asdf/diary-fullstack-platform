package com.example.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.diary.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 标签数据访问。
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
