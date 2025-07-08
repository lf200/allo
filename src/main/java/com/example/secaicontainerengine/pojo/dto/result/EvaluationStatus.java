package com.example.secaicontainerengine.pojo.dto.result;

import lombok.Data;

import java.util.Map;

@Data
public class EvaluationStatus {
    private Long modelId;
    private String metric;
    private String status;
}
