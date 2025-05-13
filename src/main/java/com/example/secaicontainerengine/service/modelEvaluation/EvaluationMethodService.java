package com.example.secaicontainerengine.service.modelEvaluation;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.secaicontainerengine.common.BaseResponse;
import com.example.secaicontainerengine.pojo.dto.model.EvaluationMethodRequest;
import com.example.secaicontainerengine.pojo.entity.EvaluationMethod;
import com.example.secaicontainerengine.pojo.entity.EvaluationResult;

import java.util.List;
import java.util.Map;

public interface EvaluationMethodService extends IService<EvaluationMethod> {

    Map<Long, String> getEvaluationMethodTypeMap(List<EvaluationResult> resultList);

    BaseResponse<?> insert(EvaluationMethodRequest evaluationMethodRequest);


}
