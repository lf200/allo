package com.example.secaicontainerengine.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 调度任务表，用于保存等待被调度的任务
 * </p>
 *
 * @author CFZ
 * @since 2025-05-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("waiting_scheduled")
@ApiModel(value="WaitingScheduled对象", description="调度任务表，用于保存等待被调度的任务")
public class WaitingScheduled implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "自增主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "待调度的模型id")
    @TableField("modelId")
    private Long modelId;


}
