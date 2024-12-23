package com.example.secaicontainerengine.pojo.dto.model;

import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.Data;

@Data
public class ResourceConfig {

    // 需要的cpu核数，以m为单位，代表比例；250m为0.25核
    private long cpuRequired;

    // 需要的内存，一般以MiB为单位，例如"64MiB"
    private String memoryRequired;

    // 需要的gpu数量
    private Integer gpuNumRequired;

    // 每个gpu需要的显存
    private Integer gpuMemoryRequired;

    // 每个GPU需要的百分比算力  5代表5%
    private Integer gpuCoreRequired;

    // todo 需要的存储空间大小

}
