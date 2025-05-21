package com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Robustness;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Builder
@Data
public class RobustnessEvaluation {

    private BigDecimal totalScore;

    private RobustnessMetricsCleanAdv robustnessMetricsCleanAdv;

    private RobustnessMetricsItem robustnessMetricsItem;

    private RobustnessMetricsMean robustnessMetricsMean;
}
