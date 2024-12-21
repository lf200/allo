package com.example.secaicontainerengine.pojo.dto.model;

import lombok.Data;

@Data
// 题目配置信息
public class ModelConfig {

    //是否需要GPU（1:需要，0:不需要）
    private Integer gpuRequired;

    // 模型任务描述
    private String description;
}
