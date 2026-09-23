-- ============================================================
-- 日记全栈平台 · 数据库初始化脚本
-- 适用版本：MySQL 8.0+
-- 字符集：utf8mb4 / utf8mb4_unicode_ci
-- 说明：
--   1. 脚本可重复执行（CREATE ... IF NOT EXISTS）。
--   2. 如需重建表结构，请先手动 DROP TABLE，再执行本脚本。
--   3. Docker 部署时本文件挂载到 /docker-entrypoint-initdb.d/。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `diary`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `diary`;

-- ------------------------------------------------------------
-- 用户表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_user`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`   VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`   VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `nickname`   VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    `avatar`     VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `email`      VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    `created_at` DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

-- ------------------------------------------------------------
-- 日记表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_diary`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT       NOT NULL COMMENT '所属用户',
    `title`      VARCHAR(200) NOT NULL COMMENT '标题',
    `content`    LONGTEXT COMMENT 'Markdown 原文',
    `summary`    VARCHAR(500)          DEFAULT NULL COMMENT '列表页摘要，冗余存储',
    `mood`       TINYINT               DEFAULT NULL COMMENT '1开心 2平静 3难过 4焦虑 5生气',
    `weather`    VARCHAR(20)           DEFAULT NULL COMMENT '天气',
    `diary_date` DATE         NOT NULL COMMENT '日记归属日期，可与创建时间不同（支持补写）',
    `is_public`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否公开：0否 1是',
    `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删',
    `created_at` DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `diary_date`),
    KEY `idx_user_mood` (`user_id`, `mood`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '日记表';

-- ------------------------------------------------------------
-- 标签表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_tag`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户',
    `name`       VARCHAR(30) NOT NULL COMMENT '标签名',
    `color`      VARCHAR(10)          DEFAULT '#1677ff' COMMENT '标签颜色',
    `created_at` DATETIME    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_id`, `name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '标签表';

-- ------------------------------------------------------------
-- 日记标签关联表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_diary_tag`
(
    `diary_id` BIGINT NOT NULL COMMENT '日记 ID',
    `tag_id`   BIGINT NOT NULL COMMENT '标签 ID',
    PRIMARY KEY (`diary_id`, `tag_id`),
    KEY `idx_tag` (`tag_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '日记标签关联表';

-- ------------------------------------------------------------
-- 附件表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_attachment`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `diary_id`   BIGINT                DEFAULT NULL COMMENT '关联日记，可为空（草稿上传）',
    `user_id`    BIGINT       NOT NULL COMMENT '所属用户',
    `url`        VARCHAR(500) NOT NULL COMMENT '文件访问地址',
    `filename`   VARCHAR(200)          DEFAULT NULL COMMENT '原始文件名',
    `size`       BIGINT                DEFAULT NULL COMMENT '文件大小（字节）',
    `mime_type`  VARCHAR(100)          DEFAULT NULL COMMENT 'MIME 类型',
    `created_at` DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_diary` (`diary_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '附件表';

-- ------------------------------------------------------------
-- 测试用户：用户名 tester / 密码 123456
-- 密文为 BCrypt（$2b$10$ 前缀），由 Node bcryptjs 生成，
-- Spring Security 的 BCryptPasswordEncoder 可直接校验。
-- 使用 INSERT ... SELECT ... WHERE NOT EXISTS 保证脚本可重复执行。
-- ------------------------------------------------------------
INSERT INTO `t_user` (`username`, `password`, `nickname`, `created_at`, `updated_at`)
SELECT 'tester', '$2b$10$8YLZJsdPYwLQA.QjYG91g.cmYqZlNgJAzX9O.j/OKzcaaSjS3ot0q', '测试用户', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `t_user` WHERE `username` = 'tester');
