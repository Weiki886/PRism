CREATE DATABASE IF NOT EXISTS prism DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE prism;

CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `username`   VARCHAR(50)  NOT NULL,
    `email`      VARCHAR(100) NOT NULL,
    `password`   VARCHAR(255) NOT NULL COMMENT 'BCrypt hash',
    `role`       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
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
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'pending',
    `gh_repo`     VARCHAR(200) NOT NULL DEFAULT '',
    `gh_pr_number` VARCHAR(20)  NOT NULL DEFAULT '',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
