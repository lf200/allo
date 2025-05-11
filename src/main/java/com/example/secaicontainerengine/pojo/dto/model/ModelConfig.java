package com.example.secaicontainerengine.pojo.dto.model;

import lombok.Data;

@Data
public class ModelConfig {

    // 模型任务描述
    private String description;

    // 模型网络名称
    private String modelNetName;

    // 模型权重文件名
    private String weightFileName;

    // 模型网络文件名
    private String modelNetFileName;

    // 模型框架
    private String framework;

    // 模型任务类型
    private String task;

}
