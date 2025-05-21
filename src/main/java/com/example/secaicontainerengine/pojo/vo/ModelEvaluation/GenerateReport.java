package com.example.secaicontainerengine.pojo.vo.ModelEvaluation;


import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Base.BaseEvaluation;
import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Robustness.RobustnessEvaluation;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
public class GenerateReport {

    private BigDecimal totalScore;

//    private BaseEvaluation baseEvaluation;

    private RobustnessEvaluation robustnessEvaluation;
}
