package com.example.diary.converter;

import com.example.diary.entity.Diary;
import com.example.diary.entity.Tag;
import com.example.diary.vo.DiaryDetailVO;
import com.example.diary.vo.DiaryVO;
import com.example.diary.vo.TagVO;

import java.util.List;

/**
 * 日记与标签的实体 → 出参转换。
 * <p>项目不使用 MapStruct：转换点少且字段固定，手写静态方法可省掉注解处理器配置，
 * 也与「不使用 Lombok、减少编译期魔法」的取向一致。</p>
 */
public final class DiaryConverter {

    private DiaryConverter() {
    }

    public static DiaryVO toListVO(Diary diary, List<TagVO> tags) {
        if (diary == null) {
            return null;
        }
        return new DiaryVO(
                diary.getId(),
                diary.getTitle(),
                diary.getSummary(),
                diary.getMood(),
                diary.getWeather(),
                diary.getDiaryDate(),
                diary.getIsPublic(),
                diary.getCreatedAt(),
                diary.getUpdatedAt(),
                tags == null ? List.of() : tags);
    }

    public static DiaryDetailVO toDetailVO(Diary diary, List<TagVO> tags) {
        if (diary == null) {
            return null;
        }
        return new DiaryDetailVO(
                diary.getId(),
                diary.getTitle(),
                diary.getContent(),
                diary.getSummary(),
                diary.getMood(),
                diary.getWeather(),
                diary.getDiaryDate(),
                diary.getIsPublic(),
                diary.getCreatedAt(),
                diary.getUpdatedAt(),
                tags == null ? List.of() : tags);
    }

    public static TagVO toTagVO(Tag tag) {
        if (tag == null) {
            return null;
        }
        return new TagVO(tag.getId(), tag.getName(), tag.getColor());
    }

    public static List<TagVO> toTagVOList(List<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream().map(DiaryConverter::toTagVO).toList();
    }
}
