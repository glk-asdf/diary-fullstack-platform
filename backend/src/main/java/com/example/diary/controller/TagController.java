package com.example.diary.controller;

import com.example.diary.common.result.Result;
import com.example.diary.dto.TagSaveDTO;
import com.example.diary.service.TagService;
import com.example.diary.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签管理接口。标签按用户隔离，同名标签不同用户可共存。
 */
@Tag(name = "标签", description = "标签的增删改查")
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "当前用户的标签列表")
    @GetMapping
    public Result<List<TagVO>> list() {
        return Result.ok(tagService.list());
    }

    @Operation(summary = "新建标签")
    @PostMapping
    public Result<TagVO> create(@Valid @RequestBody TagSaveDTO dto) {
        return Result.ok(tagService.create(dto));
    }

    @Operation(summary = "修改标签")
    @PutMapping("/{id}")
    public Result<TagVO> update(@PathVariable Long id, @Valid @RequestBody TagSaveDTO dto) {
        return Result.ok(tagService.update(id, dto));
    }

    @Operation(summary = "删除标签（同时解除与日记的关联）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return Result.ok();
    }
}
