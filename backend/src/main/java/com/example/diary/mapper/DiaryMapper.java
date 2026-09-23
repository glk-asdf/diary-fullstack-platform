package com.example.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.diary.entity.Diary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日记数据访问。
 * <p>复杂多条件筛选使用 XML 编写，简单 CRUD 直接复用 {@link BaseMapper}。</p>
 */
@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {
}
