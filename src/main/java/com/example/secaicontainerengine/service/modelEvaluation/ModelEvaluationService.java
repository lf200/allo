package com.example.secaicontainerengine.service.modelEvaluation;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.secaicontainerengine.pojo.entity.ModelEvaluation;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ModelEvaluationService extends IService<ModelEvaluation> {

    // 开始评测
    void startEvaluationPod(ModelMessage modelMessage) throws Exception;

    // 根据模型id获取模型的评测类型
    List<String> getEvaluationMethods(Long modelId);

    void updateModelScores(Long modelId, Map<String, BigDecimal> typeScoreMap);

    void handleTimeout(Long methodId);
}
