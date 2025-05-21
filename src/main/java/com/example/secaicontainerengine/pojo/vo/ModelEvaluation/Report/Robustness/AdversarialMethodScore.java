package com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Robustness;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdversarialMethodScore {

    private BigDecimal fgsm;

    private BigDecimal pgd;
}
