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
DROP TABLE IF EXISTS contact_auth;
DROP TABLE IF EXISTS score_record;
DROP TABLE IF EXISTS `order`;
DROP TABLE IF EXISTS favorite;
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
    teacher_id      BIGINT       NOT NULL,
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
-- 课程评价表
-- ============================================================================
CREATE TABLE IF NOT EXISTS course_review (
    review_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    rating      TINYINT      NOT NULL,
    content     VARCHAR(500),
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
    course_id      BIGINT       NOT NULL,
    uploader_id    BIGINT       NOT NULL,
    file_url       VARCHAR(500) NOT NULL,
    file_size      BIGINT,
    download_score INT          DEFAULT 0,
    download_count INT          DEFAULT 0,
    description    VARCHAR(500),
    keyword        VARCHAR(100),
    status         TINYINT      DEFAULT 1,
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted     TINYINT      DEFAULT 0,
    INDEX idx_course (course_id),
    INDEX idx_uploader (uploader_id),
    INDEX idx_material_name (material_name)
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
(4, '通识拓展', 4);

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
 '信达楼 B302', NULL, 1, 468, 4.8, 1, '涵盖数组、链表、树、图等核心内容', 1, 1),
(2, 'SE318', 'Web 前端工程实践', 2, 3.0, 3, '2025-2026-2', '周三 5-6 节',
 '实验楼 A411', NULL, 2, 392, 4.7, 1, 'Vue3+TS现代前端开发实践', 1, 2),
(3, 'CS305', '数据库系统原理', 1, 4.0, 4, '2025-2026-2', '周二 1-2 节',
 '二教 204', NULL, 1, 356, 4.6, 1, 'SQL与NoSQL数据库原理', 1, 3),
(4, 'AI201', '机器学习导论', 2, 3.0, 2, '2025-2026-2', '周四 7-8 节',
 '信达楼 C106', NULL, 3, 281, 4.9, 1, '经典机器学习算法与实践', 1, 4),
(5, 'SE210', '软件工程与项目管理', 1, 3.0, 3, '2025-2026-2', '周五 3-4 节',
 '一教 305', NULL, 2, 318, 4.5, 1, '敏捷开发与项目管理实践', 1, 5),
(6, 'CS260', '网络安全基础', 2, 2.0, 2, '2025-2026-2', '周二 9-10 节',
 '实验楼 B208', NULL, 1, 245, 4.4, 1, '网络攻防与安全防护技术', 1, 6);

-- Banner
INSERT INTO banner (banner_id, title, image_url, link_url, sort_order, status) VALUES
(1, '选课周提醒', '', '/course', 1, 1),
(2, '资料共享计划', '', '/material', 2, 1),
(3, '积分兑换上新', '', '/mall', 3, 1);

-- 商品
INSERT INTO goods (goods_id, goods_name, category, price_score, stock, exchange_limit, description, status) VALUES
(1, '校园咖啡券', 1, 88, 32, 1, '兑换后可在校内合作咖啡店核销一杯指定饮品', 1),
(2, '图书馆研修间预约券', 3, 120, 14, 1, '可优先预约2小时研修间，适合小组讨论', 1),
(3, '云盘扩容5GB', 2, 160, 80, 1, '为学习资料空间扩容，有效期一学期', 1);

-- ============================================================================
-- 应用用户授权
-- ============================================================================
CREATE USER IF NOT EXISTS 'bgss_user'@'localhost' IDENTIFIED BY 'bgss123456';
CREATE USER IF NOT EXISTS 'bgss_user'@'127.0.0.1' IDENTIFIED BY 'bgss123456';
GRANT ALL PRIVILEGES ON bgss_xtt.* TO 'bgss_user'@'localhost';
GRANT ALL PRIVILEGES ON bgss_xtt.* TO 'bgss_user'@'127.0.0.1';
FLUSH PRIVILEGES;
