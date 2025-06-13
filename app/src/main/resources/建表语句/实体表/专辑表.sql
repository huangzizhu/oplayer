CREATE TABLE `album` (
    `album_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '专辑ID（唯一标识）',
    `album_name` VARCHAR(255) NOT NULL COMMENT '专辑名称',
    `artist` VARCHAR(255) NOT NULL COMMENT '艺术家',
    PRIMARY KEY (`album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专辑表';