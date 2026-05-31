CREATE DATABASE IF NOT EXISTS prism DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE prism;

CREATE TABLE IF NOT EXISTS `user` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `username`     VARCHAR(50)  NOT NULL,
    `email`        VARCHAR(100) NOT NULL,
    `password`     VARCHAR(255) COMMENT 'BCrypt hash，GitHub OAuth 用户可为空',
    `role`         VARCHAR(20)  NOT NULL DEFAULT 'USER',
    `github_id`    BIGINT       COMMENT 'GitHub 用户 ID',
    `github_login` VARCHAR(100) COMMENT 'GitHub 用户名',
    `github_token` VARCHAR(500) COMMENT 'GitHub OAuth access_token',
    `avatar_url`   VARCHAR(500) COMMENT '头像 URL',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_github_id` (`github_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `review` (
    `id`          VARCHAR(64)  NOT NULL PRIMARY KEY,
    `pr_url`      VARCHAR(500) NOT NULL,
    `pr_title`    VARCHAR(500) NOT NULL,
    `author`      VARCHAR(100) NOT NULL DEFAULT '',
    `user_id`     BIGINT       NOT NULL,
    `summary`     TEXT,
    `risks_json`  TEXT,
    `suggestions_json` TEXT,
    `context_info_json` TEXT COMMENT '本次分析使用的上下文信息（JSON）',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'pending',
    `gh_repo`     VARCHAR(200) NOT NULL DEFAULT '',
    `gh_pr_number` VARCHAR(20)  NOT NULL DEFAULT '',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `risk_feedback` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `review_id`  VARCHAR(64)  NOT NULL COMMENT '所属审查记录 ID',
    `risk_index` INT          NOT NULL COMMENT '风险在 risks 数组中的下标',
    `user_id`    BIGINT       NOT NULL COMMENT '反馈用户 ID',
    `feedback`   VARCHAR(20)  NOT NULL COMMENT '反馈类型：FALSE_POSITIVE 误报 / CONFIRMED 确认',
    `comment`    VARCHAR(500) DEFAULT '' COMMENT '反馈补充说明',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_review_risk_user` (`review_id`, `risk_index`, `user_id`),
    INDEX `idx_review_id` (`review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
# 已有数据库升级用（首次建库可忽略以下语句）
# 若 user / review / risk_feedback 表已存在且无 deleted 列，执行：
# ALTER TABLE `user`          ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除';
# ALTER TABLE `review`        ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除';
# ALTER TABLE `risk_feedback` ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除';

# 若 review 表无 context_info_json 列，执行：
# ALTER TABLE `review` ADD COLUMN `context_info_json` TEXT COMMENT '本次分析使用的上下文信息（JSON）';

# 若 user 表无 GitHub OAuth 字段，执行：
# ALTER TABLE `user` ADD COLUMN `github_id` BIGINT COMMENT 'GitHub 用户 ID';
# ALTER TABLE `user` ADD COLUMN `github_login` VARCHAR(100) COMMENT 'GitHub 用户名';
# ALTER TABLE `user` ADD COLUMN `github_token` VARCHAR(500) COMMENT 'GitHub OAuth access_token';
# ALTER TABLE `user` ADD COLUMN `avatar_url` VARCHAR(500) COMMENT '头像 URL';
# ALTER TABLE `user` ADD UNIQUE KEY `uk_github_id` (`github_id`);
# ALTER TABLE `user` MODIFY COLUMN `password` VARCHAR(255) COMMENT 'BCrypt hash，GitHub OAuth 用户可为空';
-- ============================================================
