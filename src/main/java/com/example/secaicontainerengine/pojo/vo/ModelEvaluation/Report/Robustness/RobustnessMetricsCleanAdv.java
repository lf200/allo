package com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Robustness;



import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.CleanAdv;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RobustnessMetricsCleanAdv {

    // 环境干扰
    private CleanAdv environmentMetric;

    // 对抗攻击
    private CleanAdv adversarialMetric;

    // 置信度方差
    private CleanAdv confidenceVarianceMetric;

    // 梯度敏感度
    private CleanAdv gradientSensitivityMetric;
}
