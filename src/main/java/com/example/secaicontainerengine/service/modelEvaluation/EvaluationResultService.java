package com.example.secaicontainerengine.service.modelEvaluation;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.secaicontainerengine.pojo.entity.EvaluationResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface EvaluationResultService extends IService<EvaluationResult> {

    void calculateAndUpdateScores(Long modelId);

    List<EvaluationResult> getEvaluationResultsByModelId(Long modelId);

    Map<String, BigDecimal> calculateScoresByType(List<EvaluationResult> resultList,
                                                      Map<Long, String> evaluateMethodTypeMap);

    void updateEvaluationResultScore(List<EvaluationResult> resultList);
}
