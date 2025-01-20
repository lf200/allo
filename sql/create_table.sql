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
    id             bigint auto_increment comment 'id' primary key,
    containerName varchar(256)                       not null comment '容器名称',
    containerId   varchar(512)                       not null comment '容器ID',
    nameSpace     varchar(256)                       null comment '容器命名空间',
    status         varchar(50)                        null comment '容器状态',
    restarts       int                                null comment '容器重启次数',
    AGE            varchar(50)                        null comment '容器存活时间',
    nodeName      varchar(256)                       null comment '所在节点名称',
    imageId       int                                not null comment '关联的镜像ID',
    userId        int                                not null comment '当前容器所属用户ID',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete       tinyint  default 0                 not null comment '是否删除'
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