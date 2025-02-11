package com.example.secaicontainerengine.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Container implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String containerName;

    private String containerId;

    private String nameSpace;

    private String status;

    private Integer restarts;

    private String AGE;

    private String nodeName;

    private Long imageId;

    private Long userId;

    private Long modelId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableLogic
    private int isDelete;

}
