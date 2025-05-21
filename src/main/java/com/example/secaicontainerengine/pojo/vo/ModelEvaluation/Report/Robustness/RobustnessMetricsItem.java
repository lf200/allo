package com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Robustness;


import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Robustness.Environment.Vision.TargetClass.EvtcMethodScore;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobustnessMetricsItem {

    // 环境干扰
    private EvtcMethodScore environmentMetric;

    // 对抗攻击
    private AdversarialMethodScore adversarialMetric;

    // 置信度方差
    private AdversarialMethodScore confidenceVarianceMetric;

    // 梯度敏感度
    private AdversarialMethodScore gradientSensitivityMetric;
}
