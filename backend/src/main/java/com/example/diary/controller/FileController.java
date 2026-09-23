package com.example.diary.controller;

import com.example.diary.common.result.Result;
import com.example.diary.service.FileService;
import com.example.diary.vo.FileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口。需要认证，且会记录到 t_attachment。
 */
@Tag(name = "文件", description = "图片上传")
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(summary = "上传图片，返回可直接访问的 URL")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileVO> upload(@RequestPart("file") MultipartFile file) {
        return Result.ok(fileService.upload(file));
    }
}
