package com.example.diary.service.impl;

import com.example.diary.common.exception.BizException;
import com.example.diary.common.result.ResultCode;
import com.example.diary.common.util.SecurityUtil;
import com.example.diary.config.MinioProperties;
import com.example.diary.entity.Attachment;
import com.example.diary.mapper.AttachmentMapper;
import com.example.diary.service.FileService;
import com.example.diary.vo.FileVO;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM");

    /** 白名单：仅允许图片，避免上传可执行文件 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final AttachmentMapper attachmentMapper;

    public FileServiceImpl(MinioClient minioClient,
                           MinioProperties properties,
                           AttachmentMapper attachmentMapper) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public FileVO upload(MultipartFile file) {
        validate(file);

        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? StringUtils.getFilename(file.getOriginalFilename())
                : "image";
        String extension = StringUtils.getFilenameExtension(originalFilename).toLowerCase(Locale.ROOT);

        // 随机对象名 + 按年月分目录：杜绝文件名冲突与路径穿越
        String objectName = LocalDate.now().format(DATE_DIR) + "/" + UUID.randomUUID() + "." + extension;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("上传图片到 MinIO 失败: object={}", objectName, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "图片上传失败，请稍后重试");
        }

        String url = properties.publicEndpoint() + "/" + properties.bucket() + "/" + objectName;

        Attachment attachment = new Attachment();
        attachment.setUserId(SecurityUtil.getCurrentUserId());
        attachment.setUrl(url);
        attachment.setFilename(originalFilename);
        attachment.setSize(file.getSize());
        attachment.setMimeType(file.getContentType());
        attachmentMapper.insert(attachment);

        return new FileVO(attachment.getId(), url, originalFilename, file.getSize(), file.getContentType());
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择要上传的文件");
        }

        if (file.getSize() > properties.maxFileSize()) {
            long limitMb = properties.maxFileSize() / 1024 / 1024;
            throw new BizException(ResultCode.BAD_REQUEST, "图片不能超过 " + limitMb + "MB");
        }

        String filename = file.getOriginalFilename();
        String extension = StringUtils.hasText(filename) ? StringUtils.getFilenameExtension(filename) : null;
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持 jpg / jpeg / png / gif / webp 格式");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件类型不被识别为图片");
        }
    }
}
