package com.example.diary.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 bucket 存在，并设置为「公开只读」。
 * <p>日记里的图片 URL 需要长期有效，因此不采用会过期的预签名 URL；
 * 对象名含 UUID，不可枚举，本地/内网场景下公开读可接受。</p>
 * <p>MinIO 不可用时只记录警告，不阻断应用启动。</p>
 */
@Component
public class MinioBucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketInitializer.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioBucketInitializer(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bucket = properties.bucket();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("已创建 MinIO bucket: {}", bucket);
            }
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket))
                    .build());
            log.info("MinIO bucket 就绪: {}（公开只读）", bucket);
        } catch (Exception e) {
            log.warn("MinIO bucket 初始化失败，上传接口将不可用: {}", e.getMessage());
        }
    }

    /** 只允许匿名 GetObject，不允许列举对象 */
    private String publicReadPolicy(String bucket) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": { "AWS": ["*"] },
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);
    }
}
