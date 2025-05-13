package com.example.secaicontainerengine.pojo.entity;


import com.baomidou.mybatisplus.annotation.*;
import com.example.secaicontainerengine.pojo.dto.result.PodResult;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@TableName("evaluation_result")
public class EvaluationResult implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    private Long userId;

    private Long evaluateMethodId;

    private BigDecimal score;

    private String result;

    private String status;

    private String evaluateParameters;

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
