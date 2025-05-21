package com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Robustness;


import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.CleanAdv;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RobustnessMetricsMean {

    // 环境干扰
    private BigDecimal environmentMetric;

    // 对抗攻击
    private BigDecimal adversarialMetric;

    // 置信度方差
    private BigDecimal confidenceVarianceMetric;

    // 梯度敏感度
    private BigDecimal gradientSensitivityMetric;
}
