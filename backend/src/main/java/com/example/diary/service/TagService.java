package com.example.diary.service;

import com.example.diary.dto.TagSaveDTO;
import com.example.diary.vo.TagVO;

import java.util.List;

public interface TagService {

    /** 当前用户的全部标签，按创建时间排序 */
    List<TagVO> list();

    TagVO create(TagSaveDTO dto);

    TagVO update(Long id, TagSaveDTO dto);

    /** 删除标签，同时解除与日记的关联 */
    void delete(Long id);
}
