package com.example.secaicontainerengine.service.modelEvaluation;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.secaicontainerengine.pojo.entity.EvaluationResult;
import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.GenerateReport;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface EvaluationResultService extends IService<EvaluationResult> {

    void calculateAndUpdateScores(Long modelId);

    List<EvaluationResult> getEvaluationResultsByModelId(Long modelId);

    Map<String, BigDecimal> calculateScoresByType(List<EvaluationResult> resultList,
                                                      Map<Long, String> evaluateMethodTypeMap);

    void updateEvaluationResultScore(List<EvaluationResult> resultList);

    GenerateReport getResultReport(Long modelId);

    GenerateReport calculateEvaluationReportData(List<EvaluationResult> resultList);
}
