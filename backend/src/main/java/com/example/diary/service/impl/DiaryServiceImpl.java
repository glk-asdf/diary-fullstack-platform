package com.example.diary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.diary.common.exception.BizException;
import com.example.diary.common.result.PageResult;
import com.example.diary.common.result.ResultCode;
import com.example.diary.common.util.SecurityUtil;
import com.example.diary.converter.DiaryConverter;
import com.example.diary.dto.DiaryQueryDTO;
import com.example.diary.dto.DiarySaveDTO;
import com.example.diary.entity.Diary;
import com.example.diary.entity.DiaryTag;
import com.example.diary.entity.Tag;
import com.example.diary.mapper.DiaryMapper;
import com.example.diary.mapper.DiaryTagMapper;
import com.example.diary.mapper.TagMapper;
import com.example.diary.service.DiaryService;
import com.example.diary.vo.DiaryDetailVO;
import com.example.diary.vo.DiaryVO;
import com.example.diary.vo.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DiaryServiceImpl implements DiaryService {

    /** 摘要最大长度，超出后截断（不追加省略号，保持纯文本便于搜索） */
    private static final int SUMMARY_MAX_LENGTH = 120;

    private final DiaryMapper diaryMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final TagMapper tagMapper;

    public DiaryServiceImpl(DiaryMapper diaryMapper,
                            DiaryTagMapper diaryTagMapper,
                            TagMapper tagMapper) {
        this.diaryMapper = diaryMapper;
        this.diaryTagMapper = diaryTagMapper;
        this.tagMapper = tagMapper;
    }

    @Override
    public PageResult<DiaryVO> page(DiaryQueryDTO query) {
        Long userId = SecurityUtil.getCurrentUserId();

        LambdaQueryWrapper<Diary> wrapper = Wrappers.<Diary>lambdaQuery()
                // 列表页显式指定字段，绝不读取 LONGTEXT 的 content
                .select(Diary::getId, Diary::getTitle, Diary::getSummary, Diary::getMood, Diary::getWeather,
                        Diary::getDiaryDate, Diary::getIsPublic, Diary::getCreatedAt, Diary::getUpdatedAt)
                // 数据隔离：必须带 user_id
                .eq(Diary::getUserId, userId);

        if (StringUtils.hasText(query.keyword())) {
            String keyword = query.keyword().trim();
            wrapper.and(w -> w.like(Diary::getTitle, keyword).or().like(Diary::getSummary, keyword));
        }
        if (query.mood() != null) {
            wrapper.eq(Diary::getMood, query.mood());
        }
        if (query.startDate() != null) {
            wrapper.ge(Diary::getDiaryDate, query.startDate());
        }
        if (query.endDate() != null) {
            wrapper.le(Diary::getDiaryDate, query.endDate());
        }
        if (query.tagId() != null) {
            // 使用 {0} 占位符参数化，避免字符串拼接
            wrapper.apply("EXISTS (SELECT 1 FROM t_diary_tag dt WHERE dt.diary_id = t_diary.id AND dt.tag_id = {0})",
                    query.tagId());
        }

        wrapper.orderByDesc(Diary::getDiaryDate).orderByDesc(Diary::getId);

        IPage<Diary> page = diaryMapper.selectPage(
                Page.of(query.pageOrDefault(), query.sizeOrDefault()), wrapper);

        List<Long> diaryIds = page.getRecords().stream().map(Diary::getId).toList();
        Map<Long, List<TagVO>> tagMap = loadTags(diaryIds);

        List<DiaryVO> records = page.getRecords().stream()
                .map(diary -> DiaryConverter.toListVO(diary, tagMap.get(diary.getId())))
                .toList();

        return PageResult.of(page, records);
    }

    @Override
    public DiaryDetailVO detail(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        Diary diary = requireOwned(id, userId);

        Map<Long, List<TagVO>> tagMap = loadTags(List.of(id));
        return DiaryConverter.toDetailVO(diary, tagMap.get(id));
    }

    @Override
    @Transactional
    public DiaryDetailVO create(DiarySaveDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();

        Diary diary = new Diary();
        diary.setUserId(userId);
        applyDto(diary, dto);
        diaryMapper.insert(diary);

        replaceTags(diary.getId(), dto.tagIds(), userId);

        return detail(diary.getId());
    }

    @Override
    @Transactional
    public DiaryDetailVO update(Long id, DiarySaveDTO dto) {
        Long userId = SecurityUtil.getCurrentUserId();
        Diary diary = requireOwned(id, userId);

        applyDto(diary, dto);
        diaryMapper.updateById(diary);

        replaceTags(id, dto.tagIds(), userId);

        return detail(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 先校验归属，再逻辑删除。标签关联同步清除（物理删除），与「删除标签时先解除关联」保持一致：
        // 日记行是逻辑删除（deleted=1，内容仍在库中），但关联若保留就成了指向已删日记的孤儿行。
        // 代价是日后做回收站时只能还原日记内容、标签需用户重选——届时再改为延迟清理关联。
        requireOwned(id, userId);
        diaryMapper.deleteById(id);
        diaryTagMapper.delete(Wrappers.<DiaryTag>lambdaQuery().eq(DiaryTag::getDiaryId, id));
    }

    /** 查询归属当前用户的日记，查不到统一按「不存在」处理，避免越权探测 */
    private Diary requireOwned(Long id, Long userId) {
        Diary diary = diaryMapper.selectOne(Wrappers.<Diary>lambdaQuery()
                .eq(Diary::getId, id)
                .eq(Diary::getUserId, userId));
        if (diary == null) {
            throw new BizException(ResultCode.NOT_FOUND, "日记不存在");
        }
        return diary;
    }

    private void applyDto(Diary diary, DiarySaveDTO dto) {
        diary.setTitle(dto.title().trim());
        diary.setContent(dto.content());
        diary.setSummary(buildSummary(dto.content()));
        diary.setMood(dto.mood());
        diary.setWeather(dto.weather());
        diary.setDiaryDate(dto.diaryDate());
        diary.setIsPublic(dto.isPublic() != null && dto.isPublic() == 1 ? 1 : 0);
    }

    /**
     * 由 Markdown 正文生成纯文本摘要：去掉代码块、图片、链接语法与常见标记符，再压缩空白。
     */
    private String buildSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String plain = content
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("[#>*_`~|]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (plain.isEmpty()) {
            return null;
        }
        return plain.length() > SUMMARY_MAX_LENGTH ? plain.substring(0, SUMMARY_MAX_LENGTH) : plain;
    }

    /** 全量替换标签关联；先校验标签归属当前用户，防止越权关联他人标签 */
    private void replaceTags(Long diaryId, List<Long> tagIds, Long userId) {
        diaryTagMapper.delete(Wrappers.<DiaryTag>lambdaQuery().eq(DiaryTag::getDiaryId, diaryId));

        if (CollectionUtils.isEmpty(tagIds)) {
            return;
        }

        List<Long> distinctTagIds = tagIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctTagIds.isEmpty()) {
            return;
        }

        Long ownedCount = tagMapper.selectCount(Wrappers.<Tag>lambdaQuery()
                .in(Tag::getId, distinctTagIds)
                .eq(Tag::getUserId, userId));
        if (ownedCount == null || ownedCount != distinctTagIds.size()) {
            throw new BizException(ResultCode.BAD_REQUEST, "存在无效或不属于你的标签");
        }

        distinctTagIds.forEach(tagId -> diaryTagMapper.insert(new DiaryTag(diaryId, tagId)));
    }

    /**
     * 批量装配日记标签，避免 N+1：先查出关联，再一次性取出标签，最后在内存分组。
     */
    private Map<Long, List<TagVO>> loadTags(List<Long> diaryIds) {
        if (CollectionUtils.isEmpty(diaryIds)) {
            return Map.of();
        }

        List<DiaryTag> relations = diaryTagMapper.selectList(
                Wrappers.<DiaryTag>lambdaQuery().in(DiaryTag::getDiaryId, diaryIds));
        if (relations.isEmpty()) {
            return Map.of();
        }

        List<Long> tagIds = relations.stream().map(DiaryTag::getTagId).distinct().toList();
        List<Tag> tags = tagMapper.selectList(Wrappers.<Tag>lambdaQuery().in(Tag::getId, tagIds));
        if (tags.isEmpty()) {
            return Map.of();
        }

        Map<Long, Tag> tagById = new HashMap<>();
        tags.forEach(tag -> tagById.put(tag.getId(), tag));

        Map<Long, List<TagVO>> result = new HashMap<>();
        for (DiaryTag relation : relations) {
            Tag tag = tagById.get(relation.getTagId());
            if (tag != null) {
                result.computeIfAbsent(relation.getDiaryId(), key -> new ArrayList<>())
                        .add(DiaryConverter.toTagVO(tag));
            }
        }
        return result;
    }
}
