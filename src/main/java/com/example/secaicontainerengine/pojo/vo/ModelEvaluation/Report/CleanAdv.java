package com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CleanAdv {

    private BigDecimal accClean;

    private BigDecimal accAdv;
}
