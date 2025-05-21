package com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Robustness.Environment.Vision.TargetClass;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvtcMethodScore {

    private BigDecimal occlusion;

    private BigDecimal illumination;

    private BigDecimal blur;
}
