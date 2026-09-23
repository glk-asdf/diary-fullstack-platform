package com.example.diary.service;

import com.example.diary.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /** 校验并上传图片，返回可公开访问的 URL */
    FileVO upload(MultipartFile file);
}
