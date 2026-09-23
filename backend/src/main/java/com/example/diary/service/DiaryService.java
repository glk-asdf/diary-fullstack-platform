package com.example.diary.service;

import com.example.diary.common.result.PageResult;
import com.example.diary.dto.DiaryQueryDTO;
import com.example.diary.dto.DiarySaveDTO;
import com.example.diary.vo.DiaryDetailVO;
import com.example.diary.vo.DiaryVO;

public interface DiaryService {

    /** 分页查询当前用户的日记，支持关键词 / 心情 / 日期区间 / 标签筛选 */
    PageResult<DiaryVO> page(DiaryQueryDTO query);

    /** 日记详情，含 Markdown 正文与标签 */
    DiaryDetailVO detail(Long id);

    DiaryDetailVO create(DiarySaveDTO dto);

    DiaryDetailVO update(Long id, DiarySaveDTO dto);

    /** 逻辑删除 */
    void delete(Long id);
}
