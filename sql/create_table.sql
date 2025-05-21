# 数据库初始化

-- 创建库
create database if not exists SecAI_backend;

-- 切换库
use SecAI_backend;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除'
) comment '用户' collate = utf8mb4_unicode_ci;

-- 容器表
create table if not exists container
(
    id            bigint auto_increment comment 'id' primary key,
    imageId       bigint                             not null comment '关联的镜像ID',
    modelId       bigint                             not null comment '当前容器所属的模型id',
    containerName varchar(256)                       not null comment '容器名称',
    nameSpace     varchar(256)                       null comment '容器命名空间',
    status        varchar(50)                        null comment '容器状态',
    restarts      int                                null comment '容器重启次数',
    AGE           varchar(50)                        null comment '容器存活时间',
    nodeName      varchar(256)                       null comment '所在节点名称',
    createTime    datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '是否删除'
) comment '容器' collate = utf8mb4_unicode_ci;


-- 模型信息表
create table if not exists model_message
(
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `userId`             BIGINT       NOT NULL COMMENT '用户ID',
    `modelName`          VARCHAR(512)  COMMENT '模型名称',
    `modelAddress`       VARCHAR(512)  COMMENT '模型文件存放地址',
    `datasetAddress`     VARCHAR(512)  COMMENT '数据集存放地址',
    `environmentAddress` VARCHAR(512)  COMMENT '运行环境配置文件地址',
    `weightAddress`      VARCHAR(512)  COMMENT '权重文件存放地址',
    `parameterAddress`   VARCHAR(512) NULL COMMENT '模型超参数地址',
    `modelConfig`        TEXT         NULL COMMENT '模型信息(json数组)',
    `resourceConfig`     TEXT         NULL COMMENT '资源信息(json数组)',
    `businessConfig`     TEXT         NULL COMMENT '业务信息(json数组)',
    `allDataAddress`     VARCHAR(512) NULL COMMENT '模型所有数据的文件夹地址',
#     `status`             INT      DEFAULT 0 COMMENT '模型状态 0-未上传 1-已上传',
    `status`             INT      DEFAULT 0 COMMENT '模型状态 0-文件上传中 1-文件上传成功 2-模型评测等待中 3-模型评测中 4-模型评测成功',
    `createTime`         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDeleted`          TINYINT  DEFAULT 0 COMMENT '逻辑删除标志（0: 正常, 1: 已删除）'
) comment '模型信息表' collate = utf8mb4_unicode_ci;

-- 模型评测表
create table if not exists model_evaluation
(
   `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',   -- 唯一的ID，自动增长
   `modelId` BIGINT NOT NULL COMMENT '模型ID',               -- 模型ID
   `modelName`          VARCHAR(512)  COMMENT '模型名称',
   `userId` BIGINT NOT NULL COMMENT '用户ID',                -- 用户ID
   `modelScore` JSON COMMENT '模型评测类别得分（总得分，后门攻击，对抗攻击..）',
   `status` ENUM('待评测', '评测中', '成功', '失败') NOT NULL,
   `createTime`         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
   `updateTime`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
   `isDeleted`          TINYINT  DEFAULT 0 COMMENT '逻辑删除标志（0: 正常, 1: 已删除）'

) comment '评测信息表' collate = utf8mb4_unicode_ci;

-- 调度表
CREATE TABLE if not exists `scheduled_table` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `modelId` bigint DEFAULT NULL COMMENT '该任务关联的模型id',
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COMMENT='调度任务表，用于保存等待被调度的任务';

-- 评测方法表
CREATE TABLE IF NOT EXISTS evaluation_method (
     id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评测方法ID',
     methodName VARCHAR(50) NOT NULL UNIQUE COMMENT '评测方法名称（如fgsm、pgd）',
     methodCategory ENUM('adversarialEvaluate', 'backdoorEvaluate') NOT NULL COMMENT '大类（对抗/后门等）',
     methodType ENUM('whiteBoxEvaluate', 'blackBoxEvaluate', 'backdoorEvaluate') NOT NULL COMMENT '具体类型（白盒/黑盒/后门）',
     createTime DATETIME DEFAULT CURRENT_TIMESTAMP,
     updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     isDeleted  TINYINT  DEFAULT 0 COMMENT '逻辑删除标志（0: 正常, 1: 已删除）'
) COMMENT '评测方法表' collate = utf8mb4_unicode_ci;

-- 单次评测任务结果表
CREATE TABLE IF NOT EXISTS evaluation_result (
   id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评测结果ID',
   modelId BIGINT NOT NULL COMMENT '关联模型ID',
   userId BIGINT NOT NULL COMMENT '用户ID',                -- 用户ID
   evaluateMethodId BIGINT NOT NULL COMMENT '关联攻击方法ID（外键引用attack_method.id）',
   score DECIMAL(5, 2) COMMENT '评测得分',
   result JSON COMMENT '评测结果',
   status ENUM('待评测', '评测中', '成功', '失败') NOT NULL,
   timeUse BIGINT COMMENT '评测时间',
   cpuMemoryUse BIGINT COMMENT 'CPU内存使用量',
   gpuMemoryUse BIGINT COMMENT 'GPU内存使用量',
   evaluateParameters JSON COMMENT '本次评测的参数（如fgsm的eps=0.4，用JSON存储）',
   createTime DATETIME DEFAULT CURRENT_TIMESTAMP,
   updateTime DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
   isDeleted  TINYINT  DEFAULT 0 COMMENT '逻辑删除标志（0: 正常, 1: 已删除）'
) COMMENT '模型在具体攻击方法下的评测结果' collate = utf8mb4_unicode_ci;

-- 日志表
create table if not exists log
(
    id            bigint auto_increment primary key,
    containerName varchar(30)                        null comment '容器名称',
    namespace     varchar(30)                        null comment '容器命名空间',
    messageKey    varchar(30)                        null comment '消息key',
    messageValue  varchar(100)                       null comment '消息value',
    logTime       datetime default CURRENT_TIMESTAMP null comment '日志的生成时间'
) comment '日志表';
