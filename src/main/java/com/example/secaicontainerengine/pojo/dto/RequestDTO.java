package com.example.secaicontainerengine.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class RequestDTO {
    // 用户id
    private Long userId;
    // 镜像类型
    private String imageType;
    // 要启动的镜像和对应的参数
    private List<Object> images;

}
