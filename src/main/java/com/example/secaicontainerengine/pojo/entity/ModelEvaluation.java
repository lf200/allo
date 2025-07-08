package com.example.secaicontainerengine.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@TableName("model_evaluation")
public class ModelEvaluation implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    private String modelName;

    private Long userId;

    private String modelScore;

    private String status;

    private String basicResult;

    private String interpretabilityResult;

    private Long createImageTime;

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
