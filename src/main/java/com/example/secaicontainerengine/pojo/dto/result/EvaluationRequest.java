package com.example.secaicontainerengine.pojo.dto.result;

import lombok.Data;

import java.util.Map;

@Data
public class EvaluationRequest {
    private Long modelId;
    private Map<String, String> result;
    private String resultColumn;
}
