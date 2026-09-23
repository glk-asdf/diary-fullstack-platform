package com.example.diary.controller;

import com.example.diary.common.result.PageResult;
import com.example.diary.common.result.Result;
import com.example.diary.dto.DiaryQueryDTO;
import com.example.diary.dto.DiarySaveDTO;
import com.example.diary.service.DiaryService;
import com.example.diary.vo.DiaryDetailVO;
import com.example.diary.vo.DiaryVO;
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

/**
 * 日记接口。所有操作均隐式限定在当前登录用户范围内。
 */
@Tag(name = "日记", description = "日记的增删改查")
@RestController
@RequestMapping("/api/v1/diaries")
public class DiaryController {

    private final DiaryService diaryService;

    public DiaryController(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    @Operation(summary = "分页查询日记列表")
    @GetMapping
    public Result<PageResult<DiaryVO>> page(DiaryQueryDTO query) {
        return Result.ok(diaryService.page(query));
    }

    @Operation(summary = "日记详情")
    @GetMapping("/{id}")
    public Result<DiaryDetailVO> detail(@PathVariable Long id) {
        return Result.ok(diaryService.detail(id));
    }

    @Operation(summary = "新建日记")
    @PostMapping
    public Result<DiaryDetailVO> create(@Valid @RequestBody DiarySaveDTO dto) {
        return Result.ok(diaryService.create(dto));
    }

    @Operation(summary = "更新日记")
    @PutMapping("/{id}")
    public Result<DiaryDetailVO> update(@PathVariable Long id, @Valid @RequestBody DiarySaveDTO dto) {
        return Result.ok(diaryService.update(id, dto));
    }

    @Operation(summary = "删除日记（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        diaryService.delete(id);
        return Result.ok();
    }
}
