package com.example.diary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 配置项，对应 {@code application.yml} 的 {@code diary.minio} 节点。
 *
 * @param endpoint       后端访问 MinIO 的地址
 * @param publicEndpoint 浏览器访问地址，与 endpoint 分离以适配容器部署
 * @param accessKey      访问密钥
 * @param secretKey      密钥
 * @param bucket         bucket 名称
 * @param maxFileSize    单文件大小上限（字节）
 */
@ConfigurationProperties(prefix = "diary.minio")
public record MinioProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket,
        long maxFileSize) {
}
