package com.example.secaicontainerengine.pojo.entity;


import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Image implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String imageName;

    private String url;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private String createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private String updateTime;

    private Long size;

    @TableLogic
    private Integer isDelete;
}
