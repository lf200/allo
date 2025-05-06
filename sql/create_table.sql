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

-- 镜像表
create table if not exists image
(
    id         bigint auto_increment comment 'id' primary key,
    imageName varchar(255)                       NOT NULL COMMENT '镜像名称',
    url        varchar(255)                       NOT NULL COMMENT '拉取地址',
    size       bigint                             NOT NULL COMMENT '镜像大小',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除'
) comment '容器' collate = utf8mb4_unicode_ci;

-- 模型信息表
create table if not exists model_message
(
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `userId`             BIGINT       NOT NULL COMMENT '用户ID',
    `modelAddress`       VARCHAR(512)  COMMENT '模型文件存放地址',
    `datasetAddress`     VARCHAR(512)  COMMENT '数据集存放地址',
    `environmentAddress` VARCHAR(512)  COMMENT '运行环境配置文件地址',
    `weightAddress`      VARCHAR(512)  COMMENT '权重文件存放地址',
    `parameterAddress`   VARCHAR(512) NULL COMMENT '模型超参数地址',
    `modelConfig`        TEXT         NULL COMMENT '模型信息(json数组)',
    `resourceConfig`     TEXT         NULL COMMENT '资源信息(json数组)',
    `businessConfig`     TEXT         NULL COMMENT '业务信息(json数组)',
    `allDataAddress`     VARCHAR(512) NULL COMMENT '模型所有数据的文件夹地址',
    `status`             INT      DEFAULT 0 COMMENT '模型状态 0-待评测(未上传) 1-待评测(已上传) 2-评测中 3-成功 4-失败',
    `createTime`         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDeleted`          TINYINT  DEFAULT 0 COMMENT '逻辑删除标志（0: 正常, 1: 已删除）'
) comment '模型信息表' collate = utf8mb4_unicode_ci;

-- 模型评测表
create table if not exists model_evaluation
(
   `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',   -- 唯一的ID，自动增长
   `modelId` BIGINT NOT NULL COMMENT '模型ID',               -- 模型ID
   `userId` BIGINT NOT NULL COMMENT '用户ID',                -- 用户ID
   `backdoorAttackScore` DECIMAL(5, 2), -- 后门攻击评测得分（例如：95.75）
   `backdoorAttackStatus` ENUM('待评测', '评测中', '已完成') NOT NULL, -- 后门攻击评测状态
   `backdoorAttackResult` JSON COMMENT '后门攻击运行结果',
   `adversarialAttackScore` DECIMAL(5, 2), -- 对抗攻击评测得分（例如：85.60）
   `adversarialAttackStatus` ENUM('待评测', '评测中', '已完成') NOT NULL, -- 对抗攻击评测状态
   `adversarialAttackResult` JSON COMMENT '对抗攻击运行结果',
   `createTime`         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
   `updateTime`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
   `isDeleted`          TINYINT  DEFAULT 0 COMMENT '逻辑删除标志（0: 正常, 1: 已删除）'

) comment '评测信息表' collate = utf8mb4_unicode_ci;

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

-- 模型评测成功触发器
create definer = root@localhost trigger container_exec_success
    after update
    on model_evaluation
    for each row
BEGIN
    IF NEW.backdoorAttackStatus = '已完成' AND NEW.adversarialAttackStatus = '已完成' THEN
        UPDATE model_message
        SET status = '3'
        WHERE id = NEW.modelId;
    END IF;
END;

-- 模型评测失败触发器
create definer = root@localhost trigger container_exec_failure
    after update
    on container
    for each row
BEGIN
    IF NEW.status = 'Failed' THEN
        UPDATE model_message
        SET status = '4'
        WHERE id = NEW.modelId;
    END IF;
END;