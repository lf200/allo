package com.example.secaicontainerengine.pojo.dto.model;


import lombok.Data;

import java.util.List;

@Data
public class EvaluationConfig {

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

    // 模型评测方法
    private List<BusinessConfig.EvaluationDimensionConfig> evaluateMethods;

    // 类别数目
    private Integer nbClasses;

    // 输入数据形状 - 图像通道数
    private Integer inputChannels;

    // 输入数据形状 - 图像高度
    private Integer inputHeight;

    // 输入数据形状 - 图像宽度
    private Integer inputWidth;

}
