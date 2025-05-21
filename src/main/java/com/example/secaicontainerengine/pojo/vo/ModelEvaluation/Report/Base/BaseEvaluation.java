package com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Base;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Builder
@Data
public class BaseEvaluation {

    private BigDecimal acc;
}
