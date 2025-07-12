package com.example.secaicontainerengine.pojo.dto.model;

import lombok.Data;

import java.util.List;

@Data
public class BusinessConfig {

    // 每个维度的配置信息
    private List<EvaluationDimensionConfig> evaluateMethods;

    @Data
    public static class EvaluationDimensionConfig {
        private String dimension; // 如 basic、robustness 等
        private List<MethodMetricPair> methodMetricMap; // 测试方法和其对应的指标
    }

    @Data
    public static class MethodMetricPair {
        private String method;           // 测试方法名称
        private List<String> metrics;    // 对应的指标
    }

}
