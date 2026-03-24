# 数据库初始化
# @author <a href="https://github.com/liyupi">程序员鱼皮</a>
# @from <a href="https://yupi.icu">编程导航知识星球</a>

-- 创建库
create database if not exists mianshiya;

-- 切换库
use mianshiya;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    unionId      varchar(256)                           null comment '微信开放平台id',
    mpOpenId     varchar(256)                           null comment '公众号openId',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    index idx_unionId (unionId)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 题库表
create table if not exists question_bank
(
    id          bigint auto_increment comment 'id' primary key,
    title       varchar(256)                       null comment '标题',
    description text                               null comment '描述',
    picture     varchar(2048)                      null comment '图片',
    userId      bigint                             not null comment '创建用户 id',
    editTime    datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    index idx_title (title)
) comment '题库' collate = utf8mb4_unicode_ci;

-- 题目表
create table if not exists question
(
    id         bigint auto_increment comment 'id' primary key,
    title      varchar(256)                       null comment '标题',
    content    text                               null comment '内容',
    tags       varchar(1024)                      null comment '标签列表（json 数组）',
    answer     text                               null comment '推荐答案',
    userId     bigint                             not null comment '创建用户 id',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    index idx_title (title),
    index idx_userId (userId)
) comment '题目' collate = utf8mb4_unicode_ci;

-- 题库题目表（硬删除）
create table if not exists question_bank_question
(
    id             bigint auto_increment comment 'id' primary key,
    questionBankId bigint                             not null comment '题库 id',
    questionId     bigint                             not null comment '题目 id',
    userId         bigint                             not null comment '创建用户 id',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    UNIQUE (questionBankId, questionId)
) comment '题库题目' collate = utf8mb4_unicode_ci;

-- 题目 AI 内容表
create table if not exists question_ai_content
(
    id                    bigint auto_increment comment 'id' primary key,
    questionId            bigint                             not null comment '题目 id',
    plainExplanation      text                               null comment '通俗讲解',
    keyPointsJson         text                               null comment '核心考点（json 数组）',
    thinkingHintsJson     text                               null comment '分步提示（json 数组）',
    pitfallsJson          text                               null comment '易错点（json 数组）',
    followUpQuestionsJson text                               null comment '面试追问（json 数组）',
    modelName             varchar(64)                        null comment '模型名称',
    version               int      default 1                 not null comment '版本号',
    status                int      default 1                 not null comment '状态',
    createTime            datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime            datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete              tinyint  default 0                 not null comment '是否删除',
    unique key uk_questionId (questionId)
) comment '题目 AI 内容' collate = utf8mb4_unicode_ci;

-- 用户题目行为记录表
create table if not exists user_question_record
(
    id             bigint auto_increment comment 'id' primary key,
    userId         bigint                             not null comment '用户 id',
    questionId     bigint                             not null comment '题目 id',
    questionBankId bigint                             null comment '题库 id',
    actionType     varchar(32)                        not null comment '行为类型',
    isCorrect      tinyint                            null comment '是否答对',
    usedAi         tinyint  default 0                 not null comment '是否使用 AI',
    viewDuration   int      default 0                 not null comment '浏览时长（秒）',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    index idx_userId_createTime (userId, createTime),
    index idx_questionId (questionId)
) comment '用户题目行为记录' collate = utf8mb4_unicode_ci;

-- 用户 AI 学习报告表
create table if not exists user_ai_report
(
    id                  bigint auto_increment comment 'id' primary key,
    userId              bigint                             not null comment '用户 id',
    reportType          varchar(32)                        not null comment '报告类型',
    startDate           date                               not null comment '开始日期',
    endDate             date                               not null comment '结束日期',
    summary             text                               null comment '总结',
    weakPointsJson      text                               null comment '薄弱点（json 数组）',
    recommendationsJson text                               null comment '建议（json 数组）',
    modelName           varchar(64)                        null comment '模型名称',
    status              int      default 1                 not null comment '状态',
    createTime          datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime          datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique key uk_user_period (userId, reportType, startDate, endDate)
) comment '用户 AI 学习报告' collate = utf8mb4_unicode_ci;
