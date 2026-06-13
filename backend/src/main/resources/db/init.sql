-- ============================================================================
-- 不高山选课通 (bgss_xtt) 数据库初始化脚本
-- MySQL 8.0+
-- 所有用户密码: 123456
-- bcrypt hash: $2b$10$QN2aisNMvz02aqZfBj9PQuGsTG7wI7aR3yT3OQ1AWXdsfhiVU.l96
-- ============================================================================

CREATE DATABASE IF NOT EXISTS bgss_xtt
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE bgss_xtt;

-- ============================================================================
-- 删除表（方便重新初始化）
-- ============================================================================
DROP TABLE IF EXISTS sms_code;
DROP TABLE IF EXISTS material_report;
DROP TABLE IF EXISTS material_download;
DROP TABLE IF EXISTS square_like;
DROP TABLE IF EXISTS square_comment;
DROP TABLE IF EXISTS square_post;
DROP TABLE IF EXISTS feedback;
DROP TABLE IF EXISTS friend;
DROP TABLE IF EXISTS contact_auth;
DROP TABLE IF EXISTS score_record;
DROP TABLE IF EXISTS `order`;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS course_selection;
DROP TABLE IF EXISTS course_review;
DROP TABLE IF EXISTS material;
DROP TABLE IF EXISTS goods;
DROP TABLE IF EXISTS banner;
DROP TABLE IF EXISTS news;
DROP TABLE IF EXISTS news_category;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS user;

-- ============================================================================
-- 用户表
-- ============================================================================
CREATE TABLE IF NOT EXISTS user (
    user_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    nickname       VARCHAR(50)  NOT NULL,
    avatar_url     VARCHAR(500),
    avatar_storage_key VARCHAR(255),
    phone          VARCHAR(20)  UNIQUE,
    email          VARCHAR(100),
    user_type      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-学生 2-教师 3-管理员',
    real_name      VARCHAR(50),
    student_no     VARCHAR(20),
    department     VARCHAR(100),
    class_name     VARCHAR(100),
    title          VARCHAR(50),
    office_location VARCHAR(200),
    office_phone   VARCHAR(20),
    score_balance  INT          DEFAULT 0,
    total_score    INT          DEFAULT 0,
    status         TINYINT      DEFAULT 1,
    last_login_time DATETIME,
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted     TINYINT      DEFAULT 0,
    INDEX idx_user_type (user_type),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 课程表
-- ============================================================================
CREATE TABLE IF NOT EXISTS course (
    course_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_code     VARCHAR(20)  NOT NULL UNIQUE,
    course_name     VARCHAR(100) NOT NULL,
    course_type     TINYINT      NOT NULL COMMENT '1-必修 2-选修 3-通识',
    credit          DECIMAL(3,1) NOT NULL,
    teacher_id      BIGINT,
    semester        VARCHAR(20),
    schedule        VARCHAR(200),
    location        VARCHAR(100),
    cover_url       VARCHAR(500),
    outline         TEXT,
    selection_count INT          DEFAULT 0,
    avg_rating      DECIMAL(3,1),
    rating_count    INT          DEFAULT 0,
    description     VARCHAR(500),
    status          TINYINT      DEFAULT 1,
    category_id     BIGINT,
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted      TINYINT      DEFAULT 0,
    INDEX idx_course_name (course_name),
    INDEX idx_teacher (teacher_id),
    INDEX idx_status (status),
    INDEX idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 用户选课记录表
-- ============================================================================
CREATE TABLE IF NOT EXISTS course_selection (
    selection_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id    BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    status       TINYINT DEFAULT 1 COMMENT '1-已选 2-已退',
    select_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    drop_time    DATETIME,
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   TINYINT DEFAULT 0,
    UNIQUE KEY uk_course_selection_user (course_id, user_id),
    INDEX idx_course_selection_user_status (user_id, status),
    INDEX idx_course_selection_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 课程评价表
-- ============================================================================
CREATE TABLE IF NOT EXISTS course_review (
    review_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    rating      TINYINT      NOT NULL,
    content     VARCHAR(500),
    is_anonymous TINYINT      DEFAULT 0,
    reward_granted TINYINT    DEFAULT 0,
    status      TINYINT      DEFAULT 1,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  TINYINT      DEFAULT 0,
    INDEX idx_course (course_id),
    INDEX idx_user (user_id),
    UNIQUE KEY uk_course_user (course_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 资料表
-- ============================================================================
CREATE TABLE IF NOT EXISTS material (
    material_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_name  VARCHAR(200) NOT NULL,
    material_type  TINYINT      NOT NULL,
    course_id      BIGINT,
    uploader_id    BIGINT       NOT NULL,
    file_url       VARCHAR(500),
    storage_key    VARCHAR(255),
    original_file_name VARCHAR(255),
    content_type   VARCHAR(100),
    file_size      BIGINT,
    download_score INT          DEFAULT 0,
    download_count INT          DEFAULT 0,
    description    VARCHAR(500),
    reject_reason  VARCHAR(500),
    keyword        VARCHAR(100),
    status         TINYINT      DEFAULT 1,
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted     TINYINT      DEFAULT 0,
    INDEX idx_course (course_id),
    INDEX idx_uploader (uploader_id),
    INDEX idx_material_name (material_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS material_report (
    report_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason      VARCHAR(500) NOT NULL,
    status      TINYINT NOT NULL DEFAULT 0 COMMENT '0-待处理 1-已处理 2-已驳回',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_material_report_material (material_id),
    INDEX idx_material_report_status (status),
    INDEX idx_material_report_reporter (reporter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS material_download (
    download_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id  BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    score_cost   INT NOT NULL DEFAULT 0,
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_material_download_user (material_id, user_id),
    INDEX idx_material_download_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 商品表
-- ============================================================================
CREATE TABLE IF NOT EXISTS goods (
    goods_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    goods_name     VARCHAR(100)  NOT NULL,
    category       TINYINT       NOT NULL,
    image_urls     VARCHAR(2000),
    price_score    INT           NOT NULL,
    stock          INT           NOT NULL,
    exchange_limit TINYINT       DEFAULT 1,
    description    TEXT,
    status         TINYINT       DEFAULT 1,
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted     TINYINT       DEFAULT 0,
    INDEX idx_category (category),
    INDEX idx_goods_name (goods_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 订单表
-- ============================================================================
CREATE TABLE IF NOT EXISTS `order` (
    order_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no      VARCHAR(32)  NOT NULL UNIQUE,
    user_id       BIGINT       NOT NULL,
    goods_id      BIGINT       NOT NULL,
    goods_name    VARCHAR(100) NOT NULL,
    score_amount  INT          NOT NULL,
    status        TINYINT      DEFAULT 1 COMMENT '1-待处理 2-处理中 3-已完成 4-已取消',
    remark        VARCHAR(200),
    cancel_time   DATETIME,
    complete_time DATETIME,
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted    TINYINT      DEFAULT 0,
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 积分记录表
-- ============================================================================
CREATE TABLE IF NOT EXISTS score_record (
    record_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    change_type   TINYINT      NOT NULL,
    change_amount INT          NOT NULL,
    balance_after INT          NOT NULL,
    source_desc   VARCHAR(200),
    related_id    BIGINT,
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_create_time (create_time),
    INDEX idx_change_type (change_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 收藏表
-- ============================================================================
CREATE TABLE IF NOT EXISTS favorite (
    favorite_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT   NOT NULL,
    target_type TINYINT  NOT NULL,
    target_id   BIGINT   NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_target (target_id),
    UNIQUE KEY uk_user_target (user_id, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Banner表
-- ============================================================================
CREATE TABLE IF NOT EXISTS banner (
    banner_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(100),
    image_url   VARCHAR(500) NOT NULL,
    link_url    VARCHAR(500),
    sort_order  INT          DEFAULT 0,
    status      TINYINT      DEFAULT 1,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  TINYINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 资讯表
-- ============================================================================
CREATE TABLE IF NOT EXISTS news (
    news_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(100) NOT NULL,
    summary     VARCHAR(200),
    content     TEXT,
    category_id BIGINT       NOT NULL,
    cover_url   VARCHAR(500),
    view_count  INT          DEFAULT 0,
    status      TINYINT      DEFAULT 1,
    publish_time DATETIME,
    create_by   BIGINT,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  TINYINT      DEFAULT 0,
    INDEX idx_title (title),
    INDEX idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 资讯分类表
-- ============================================================================
CREATE TABLE IF NOT EXISTS news_category (
    category_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL,
    sort_order    INT         DEFAULT 0,
    status        TINYINT     DEFAULT 1,
    create_time   DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 联系方式权限表
-- ============================================================================
CREATE TABLE IF NOT EXISTS contact_auth (
    auth_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT  NOT NULL UNIQUE,
    phone_visible   TINYINT DEFAULT 1 COMMENT '1-公开 2-隐藏',
    email_visible   TINYINT DEFAULT 1 COMMENT '1-公开 2-隐藏',
    contact_visible TINYINT DEFAULT 2 COMMENT '1-所有人可见 2-仅好友可见 3-不可见',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 短信验证码表
-- ============================================================================
CREATE TABLE IF NOT EXISTS sms_code (
    sms_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone       VARCHAR(20)  NOT NULL,
    code        VARCHAR(6)   NOT NULL,
    type        TINYINT      NOT NULL,
    expire_time DATETIME     NOT NULL,
    is_used     TINYINT      DEFAULT 0,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend (
    friend_id  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    target_id  BIGINT NOT NULL,
    status     TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_friend_direction (user_id, target_id),
    INDEX idx_friend_target_status (target_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS feedback (
    feedback_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    type        VARCHAR(30) NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    contact     VARCHAR(100),
    status      TINYINT NOT NULL DEFAULT 0,
    reply       VARCHAR(1000),
    reply_time  DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_feedback_user (user_id),
    INDEX idx_feedback_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS square_post (
    post_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    content       VARCHAR(2000) NOT NULL,
    course_name   VARCHAR(100),
    material_name VARCHAR(200),
    tags          VARCHAR(500),
    status        TINYINT NOT NULL DEFAULT 1,
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted    TINYINT NOT NULL DEFAULT 0,
    INDEX idx_square_post_status_time (status, create_time),
    INDEX idx_square_post_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS square_comment (
    comment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id    BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_square_comment_post (post_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS square_like (
    like_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id    BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_square_like (post_id, user_id),
    INDEX idx_square_like_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 种子数据
-- ============================================================================

-- 所有用户密码: 123456
SET @pwd = '$2b$10$QN2aisNMvz02aqZfBj9PQuGsTG7wI7aR3yT3OQ1AWXdsfhiVU.l96';

DROP TABLE IF EXISTS `category`;
CREATE TABLE IF NOT EXISTS `category` (
    `category_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID，主键，自增',
    `category_name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程分类表';

INSERT INTO `category` (`category_id`, `category_name`, `sort_order`) VALUES
(1, '计算机基础', 1),
(2, '软件开发', 2),
(3, '数据智能', 3),
(4, '通识拓展', 4),
(5, '工程实践', 5),
(6, '网络安全', 6);

-- 管理员 (user_id = 1)
INSERT INTO user (username, password_hash, nickname, user_type, department, score_balance, total_score)
VALUES ('admin', @pwd, '系统管理员', 3, '信息中心', 1000, 1000);

-- 教师1: 陈明 (user_id = 2)
INSERT INTO user (username, password_hash, nickname, real_name, user_type, title, department,
                  office_location, office_phone, email, phone)
VALUES ('chenming', @pwd, '陈明', '陈明', 2, '教授', '计算机学院',
        '信达楼B507', '02885961234', 'chenming@cuit.edu.cn', '13800000001');

-- 教师2: 刘青 (user_id = 3)
INSERT INTO user (username, password_hash, nickname, real_name, user_type, title, department,
                  office_location, office_phone, email, phone)
VALUES ('liuqing', @pwd, '刘青', '刘青', 2, '讲师', '软件工程学院',
        '实验楼A412', '02885962345', 'liuqing@cuit.edu.cn', '13800000002');

-- 教师3: 王雪 (user_id = 4)
INSERT INTO user (username, password_hash, nickname, real_name, user_type, title, department,
                  office_location, phone)
VALUES ('wangxue', @pwd, '王雪', '王雪', 2, '副教授', '计算机学院',
        '二教514', '13800000003');

-- 学生1: 张义彬 (user_id = 5)
INSERT INTO user (username, password_hash, nickname, real_name, user_type, department, class_name,
                  student_no, phone, score_balance, total_score)
VALUES ('2024051224', @pwd, '张义彬', '张义彬', 1, '计算机学院', '计科246',
        '2024051224', '13800135678', 386, 820);

-- 学生2: 李欣 (user_id = 6)
INSERT INTO user (username, password_hash, nickname, real_name, user_type, department, class_name,
                  student_no, phone, score_balance, total_score)
VALUES ('2024051208', @pwd, '李欣', '李欣', 1, '计算机学院', '计科246',
        '2024051208', '13911112222', 920, 920);

-- 学生3: 周可 (user_id = 7)
INSERT INTO user (username, password_hash, nickname, real_name, user_type, department, class_name,
                  student_no, phone, score_balance, total_score)
VALUES ('2024051216', @pwd, '周可', '周可', 1, '计算机学院', '计科246',
        '2024051216', '13722223333', 775, 775);

-- 联系方式权限 (仅学生)
INSERT INTO contact_auth (user_id, phone_visible, email_visible, contact_visible) VALUES
(5, 2, 2, 2),   -- 张义彬
(6, 2, 2, 2),   -- 李欣
(7, 1, 1, 1);   -- 周可

-- 资讯分类
INSERT INTO news_category (category_id, category_name, sort_order) VALUES
(1, '全部', 1),
(2, '课程通知', 2),
(3, '学习资讯', 3);

-- 课程 (teacher_id 对应教师: 2=陈明, 3=刘青, 4=王雪)
INSERT INTO course (course_id, course_code, course_name, course_type, credit, teacher_id,
                    semester, schedule, location, cover_url, outline, selection_count,
                    avg_rating, rating_count, description, status, category_id) VALUES
(1, 'CS203', '算法与数据结构', 1, 4.0, 2, '2025-2026-2', '周一 3-4 节',
 '信达楼 B302', NULL, NULL, 2, 4.5, 2, '涵盖数组、链表、树、图等核心内容', 1, 1),
(2, 'SE318', 'Web 前端工程实践', 2, 3.0, 3, '2025-2026-2', '周三 5-6 节',
 '实验楼 A411', NULL, NULL, 1, 5.0, 1, 'Vue3+TS现代前端开发实践', 1, 2),
(3, 'CS305', '数据库系统原理', 1, 4.0, 4, '2025-2026-2', '周二 1-2 节',
 '二教 204', NULL, NULL, 1, 4.0, 1, 'SQL与NoSQL数据库原理', 1, 3),
(4, 'AI201', '机器学习导论', 2, 3.0, 2, '2025-2026-2', '周四 7-8 节',
 '信达楼 C106', NULL, NULL, 0, NULL, 0, '经典机器学习算法与实践', 1, 4),
(5, 'SE210', '软件工程与项目管理', 1, 3.0, 3, '2025-2026-2', '周五 3-4 节',
 '一教 305', NULL, NULL, 0, NULL, 0, '敏捷开发与项目管理实践', 1, 5),
(6, 'CS260', '网络安全基础', 2, 2.0, 2, '2025-2026-2', '周六晚上 9-10 节',
 '实验楼 B208', NULL, NULL, 0, NULL, 0, '网络攻防与安全防护技术', 1, 6),
(7, 'GE101', '大学生职业发展', 3, 1.0, 4, '2025-2026-2', '周日晚上 11-12 节',
 '线上课堂', NULL, NULL, 0, NULL, 0, '用于演示管理员查看已停用课程', 0, 4);

-- 选课、退课与评价数据
INSERT INTO course_selection
    (selection_id, course_id, user_id, status, select_time, drop_time) VALUES
(1, 1, 5, 1, '2026-03-01 09:00:00', NULL),
(2, 1, 6, 1, '2026-03-01 09:05:00', NULL),
(3, 2, 5, 1, '2026-03-02 10:00:00', NULL),
(4, 3, 7, 1, '2026-03-03 11:00:00', NULL),
(5, 4, 6, 2, '2026-03-04 12:00:00', '2026-03-06 08:30:00');

INSERT INTO course_review
    (review_id, course_id, user_id, rating, content, is_anonymous, reward_granted, status, create_time) VALUES
(1, 1, 5, 5, '讲解清晰，练习题很有帮助。', 0, 1, 1, '2026-04-10 10:00:00'),
(2, 1, 6, 4, '内容扎实，作业量适中。', 1, 1, 1, '2026-04-11 11:00:00'),
(3, 2, 5, 5, '项目实践完整，适合入门。', 0, 1, 1, '2026-04-12 12:00:00'),
(4, 3, 7, 4, '数据库设计部分很实用。', 0, 1, 1, '2026-04-13 13:00:00');

-- 资料：0-待审核 1-已发布 2-已驳回 3-已下架
INSERT INTO material
    (material_id, material_name, material_type, course_id, uploader_id, file_url,
     original_file_name, content_type, file_size, download_score, download_count,
     description, reject_reason, keyword, status, create_time) VALUES
(1, '算法期末复习提纲', 1, 1, 5, '/uploads/seed/algorithm-review.pdf',
 'algorithm-review.pdf', 'application/pdf', 245760, 0, 12,
 '算法与数据结构核心知识点汇总', NULL, '算法 复习 数据结构', 1, '2026-04-01 09:00:00'),
(2, 'Web 前端项目模板', 4, 2, 6, '/uploads/seed/web-project-template.pptx',
 'web-project-template.pptx', 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
 524288, 20, 8, '课程项目结构与答辩模板', NULL, 'Vue 前端 项目', 1, '2026-04-02 10:00:00'),
(3, '数据库实验报告示例', 2, 3, 7, '/uploads/seed/database-lab.docx',
 'database-lab.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
 188416, 0, 0, '等待管理员审核的资料', NULL, '数据库 实验', 0, '2026-04-03 11:00:00'),
(4, '机器学习练习答案', 1, 4, 5, '/uploads/seed/ml-answer.pdf',
 'ml-answer.pdf', 'application/pdf', 156672, 10, 0,
 '用于演示审核驳回状态', '内容缺少来源说明，请补充后重新提交。', '机器学习 答案', 2, '2026-04-04 12:00:00'),
(5, '软件工程旧版讲义', 1, 5, 6, '/uploads/seed/se-legacy.pdf',
 'se-legacy.pdf', 'application/pdf', 204800, 0, 2,
 '用于演示已下架状态', NULL, '软件工程 旧版', 3, '2026-04-05 13:00:00'),
(6, '网络安全实验清单', 3, 6, 7, '/uploads/seed/security-labs.xlsx',
 'security-labs.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
 98304, 10, 5, '网络安全实验环境与检查项', NULL, '网络安全 实验', 1, '2026-04-06 14:00:00');

INSERT INTO material_download
    (download_id, material_id, user_id, score_cost, create_time) VALUES
(1, 2, 5, 20, '2026-04-15 09:00:00'),
(2, 1, 6, 0, '2026-04-15 09:10:00'),
(3, 6, 5, 10, '2026-04-15 09:20:00');

INSERT INTO material_report
    (report_id, material_id, reporter_id, reason, status, create_time) VALUES
(1, 2, 7, '资料中的一个外部链接已经失效。', 0, '2026-04-16 10:00:00'),
(2, 1, 6, '已核查，内容没有问题。', 1, '2026-04-16 11:00:00');

INSERT INTO favorite
    (favorite_id, user_id, target_type, target_id, create_time) VALUES
(1, 5, 1, 1, '2026-04-17 09:00:00'),
(2, 5, 2, 2, '2026-04-17 09:05:00'),
(3, 6, 1, 3, '2026-04-17 09:10:00'),
(4, 7, 2, 1, '2026-04-17 09:15:00');

-- Banner
INSERT INTO banner (banner_id, title, image_url, link_url, sort_order, status) VALUES
(1, '选课周提醒', '', '/course', 1, 1),
(2, '资料共享计划', '', '/material', 2, 1),
(3, '积分兑换上新', '', '/mall', 3, 1);

-- 商品
INSERT INTO goods (goods_id, goods_name, category, price_score, stock, exchange_limit, description, status) VALUES
(1, '校园咖啡券', 1, 88, 32, 1, '兑换后可在校内合作咖啡店核销一杯指定饮品', 1),
(2, '图书馆研修间预约券', 3, 120, 14, 1, '可优先预约2小时研修间，适合小组讨论', 1),
(3, '云盘扩容5GB', 2, 160, 80, 1, '为学习资料空间扩容，有效期一学期', 1),
(4, '校徽纪念徽章', 1, 60, 20, 1, '用于演示已停用商品', 0),
(5, '活动限定笔记本', 1, 200, 0, 0, '用于演示库存和兑换限制', 1);

INSERT INTO `order`
    (order_id, order_no, user_id, goods_id, goods_name, score_amount, status,
     remark, cancel_time, complete_time, create_time) VALUES
(1, 'BG202604180001', 5, 1, '校园咖啡券', 88, 1,
 '请发送电子兑换码', NULL, NULL, '2026-04-18 09:00:00'),
(2, 'BG202604180002', 6, 2, '图书馆研修间预约券', 120, 3,
 NULL, NULL, '2026-04-18 15:00:00', '2026-04-18 10:00:00'),
(3, 'BG202604180003', 7, 3, '云盘扩容5GB', 160, 4,
 '重复兑换', '2026-04-18 11:30:00', NULL, '2026-04-18 11:00:00');

INSERT INTO score_record
    (record_id, user_id, change_type, change_amount, balance_after, source_desc, related_id, create_time) VALUES
(1, 5, 1, 10, 406, '提交课程评价奖励', 1, '2026-04-10 10:00:00'),
(2, 5, 2, -20, 386, '下载积分资料', 2, '2026-04-15 09:00:00'),
(3, 6, 2, -120, 920, '兑换图书馆研修间预约券', 2, '2026-04-18 10:00:00'),
(4, 7, 1, 10, 775, '资料被下载奖励', 6, '2026-04-15 09:20:00');

INSERT INTO news
    (news_id, title, summary, content, category_id, view_count, status, publish_time, create_by) VALUES
(1, '2025-2026 学年第二学期选课提醒',
 '请同学们在规定时间内完成选课与退补选。',
 '选课开放后请及时确认课程时间，避免时间冲突。', 2, 128, 1, '2026-02-20 09:00:00', 1),
(2, '资料共享规范更新',
 '上传资料时请注明来源并检查文件可用性。',
 '涉及版权或隐私的信息不得上传，违规资料将被下架。', 3, 86, 1, '2026-03-01 10:00:00', 1),
(3, '旧版通知示例',
 '用于管理员查看停用资讯。',
 '这是一条未发布的示例资讯。', 2, 0, 0, NULL, 1);

INSERT INTO friend
    (friend_id, user_id, target_id, status, create_time) VALUES
(1, 5, 6, 1, '2026-04-19 09:00:00'),
(2, 7, 5, 0, '2026-04-19 09:10:00');

INSERT INTO feedback
    (feedback_id, user_id, type, content, contact, status, reply, reply_time, create_time) VALUES
(1, 5, '功能建议', '希望课程列表可以按上课时间筛选。', '2024051224@stu.example.edu', 1,
 '已在本轮版本中增加时间筛选。', '2026-04-20 16:00:00', '2026-04-20 09:00:00'),
(2, 6, '问题反馈', '资料列表需要区分免费和积分资料。', NULL, 0,
 NULL, NULL, '2026-04-21 10:00:00');

INSERT INTO square_post
    (post_id, user_id, content, course_name, material_name, tags, status, create_time, is_deleted) VALUES
(1, 5, '算法复习可以先按数据结构分类，再集中刷题。', '算法与数据结构', '算法期末复习提纲',
 '算法,复习', 1, '2026-04-22 09:00:00', 0),
(2, 6, '前端课程项目建议尽早确定组件规范。', 'Web 前端工程实践', NULL,
 '前端,项目', 1, '2026-04-22 10:00:00', 0),
(3, 7, '这是一条管理员可见的停用动态。', NULL, NULL,
 '测试', 0, '2026-04-22 11:00:00', 0),
(4, 5, '这是一条已删除动态。', NULL, NULL,
 '测试', 1, '2026-04-22 12:00:00', 1);

INSERT INTO square_comment
    (comment_id, post_id, user_id, content, create_time, is_deleted) VALUES
(1, 1, 6, '这个复习顺序很实用。', '2026-04-22 09:30:00', 0),
(2, 1, 7, '可以再补充复杂度分析。', '2026-04-22 09:40:00', 0),
(3, 2, 5, '同意，先统一目录结构会省很多时间。', '2026-04-22 10:30:00', 0);

INSERT INTO square_like
    (like_id, post_id, user_id, create_time) VALUES
(1, 1, 6, '2026-04-22 09:20:00'),
(2, 1, 7, '2026-04-22 09:25:00'),
(3, 2, 5, '2026-04-22 10:20:00');

-- Database users and grants are managed outside this schema through deployment tooling.
