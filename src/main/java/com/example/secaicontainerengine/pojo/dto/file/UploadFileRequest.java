package com.example.secaicontainerengine.pojo.dto.file;


import com.example.secaicontainerengine.pojo.dto.model.ModelConfig;
import lombok.Data;

import java.io.Serializable;

@Data
public class UploadFileRequest implements Serializable {
    /**
     * 业务
     */
    private String biz;

    private ModelConfig modelConfig;

    private static final long serialVersionUID = 1L;
}
