package com.example.secaicontainerengine.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("model_message")
public class ModelMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String modelName;

    private String modelAddress;

    private String datasetAddress;

    private String environmentAddress;

    private String weightAddress;

    // 模型配置信息（json 对象）
    // 比如模型任务描述
    private String modelConfig;

    // 模型需要的资源信息（json 对象）
    private String resourceConfig;

    // 模型业务信息（json 对象），比如后门攻击，对抗攻击
    private String businessConfig;

    // 超参数地址
    private String parameterAddress;

    // 所有数据文件夹地址
    private String allDataAddress;

    private String showBusinessConfig;

    // 模型评测状态
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}
