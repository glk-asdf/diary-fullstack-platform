package com.example.diary.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.diary.common.exception.BizException;
import com.example.diary.common.result.ResultCode;
import com.example.diary.common.util.SecurityUtil;
import com.example.diary.converter.DiaryConverter;
import com.example.diary.dto.TagSaveDTO;
import com.example.diary.entity.DiaryTag;
import com.example.diary.entity.Tag;
import com.example.diary.mapper.DiaryTagMapper;
import com.example.diary.mapper.TagMapper;
import com.example.diary.service.TagService;
import com.example.diary.vo.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TagServiceImpl implements TagService {

    private static final String DEFAULT_COLOR = "#1677ff";

    private final TagMapper tagMapper;
    private final DiaryTagMapper diaryTagMapper;

    public TagServiceImpl(TagMapper tagMapper, DiaryTagMapper diaryTagMapper) {
        this.tagMapper = tagMapper;
        this.diaryTagMapper = diaryTagMapper;
    }

    @Override
    public List<TagVO> list() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<Tag> tags = tagMapper.selectList(Wrappers.<Tag>lambdaQuery()
                .eq(Tag::getUserId, userId)
                .orderByAsc(Tag::getCreatedAt)
                .orderByAsc(Tag::getId));
        return DiaryConverter.toTagVOList(tags);
    }

    @Override
    @Transactional
    public TagVO create(TagSaveDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        String name = dto.name().trim();

        // 数据库有 uk_user_name 唯一索引，这里提前校验以返回友好提示
        if (existsName(userId, name, null)) {
            throw new BizException(ResultCode.BAD_REQUEST, "标签名已存在");
        }

        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(StringUtils.hasText(dto.color()) ? dto.color() : DEFAULT_COLOR);
        tagMapper.insert(tag);

        return DiaryConverter.toTagVO(tag);
    }

    @Override
    @Transactional
    public TagVO update(Long id, TagSaveDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        Tag tag = requireOwned(id, userId);

        String name = dto.name().trim();
        if (existsName(userId, name, id)) {
            throw new BizException(ResultCode.BAD_REQUEST, "标签名已存在");
        }

        tag.setName(name);
        if (StringUtils.hasText(dto.color())) {
            tag.setColor(dto.color());
        }
        tagMapper.updateById(tag);

        return DiaryConverter.toTagVO(tag);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        requireOwned(id, userId);

        // 先解除关联，否则日记详情里会残留指向不存在标签的脏关联
        diaryTagMapper.delete(Wrappers.<DiaryTag>lambdaQuery().eq(DiaryTag::getTagId, id));
        tagMapper.deleteById(id);
    }

    private Tag requireOwned(Long id, Long userId) {
        Tag tag = tagMapper.selectOne(Wrappers.<Tag>lambdaQuery()
                .eq(Tag::getId, id)
                .eq(Tag::getUserId, userId));
        if (tag == null) {
            throw new BizException(ResultCode.NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    /** 同用户下标签名唯一；excludeId 用于修改时排除自身 */
    private boolean existsName(Long userId, String name, Long excludeId) {
        Long count = tagMapper.selectCount(Wrappers.<Tag>lambdaQuery()
                .eq(Tag::getUserId, userId)
                .eq(Tag::getName, name)
                .ne(excludeId != null, Tag::getId, excludeId));
        return count != null && count > 0;
    }
}
