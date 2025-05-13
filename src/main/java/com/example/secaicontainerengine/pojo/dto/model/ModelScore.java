package com.example.secaicontainerengine.pojo.dto.model;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ModelScore {

    private BigDecimal whiteBoxEvaluate;

    private BigDecimal blackBoxEvaluate;

    private BigDecimal adversarialEvaluate;

    private BigDecimal backdoorEvaluate;

    private BigDecimal totalEvaluate;
}
