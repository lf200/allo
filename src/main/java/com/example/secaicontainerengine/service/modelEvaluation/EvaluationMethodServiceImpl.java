package com.example.secaicontainerengine.service.modelEvaluation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.common.BaseResponse;
import com.example.secaicontainerengine.common.ErrorCode;
import com.example.secaicontainerengine.common.ResultUtils;
import com.example.secaicontainerengine.mapper.EvaluationMethodMapper;
import com.example.secaicontainerengine.pojo.dto.model.EvaluationMethodRequest;
import com.example.secaicontainerengine.pojo.entity.EvaluationMethod;
import com.example.secaicontainerengine.pojo.entity.EvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EvaluationMethodServiceImpl extends ServiceImpl<EvaluationMethodMapper, EvaluationMethod> implements EvaluationMethodService{

    @Autowired
    private EvaluationMethodMapper evaluationMethodMapper;

    @Override
    public Map<Long, String> getEvaluationMethodTypeMap(List<EvaluationResult> resultList) {
        // 提取所有 evaluateMethodId（去重）
        Set<Long> evaluateMethodIds = resultList.stream()
                .map(EvaluationResult::getEvaluateMethodId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 查询 evaluation_method 表，获取每个 evaluateMethodId 对应的分类（methodType）
        QueryWrapper<EvaluationMethod> methodWrapper = new QueryWrapper<>();
        methodWrapper.in("id", evaluateMethodIds);
        List<EvaluationMethod> methodList = evaluationMethodMapper.selectList(methodWrapper);

        // 转换为 Map：evaluateMethodId -> methodType
        return methodList.stream()
                .collect(Collectors.toMap(EvaluationMethod::getId, EvaluationMethod::getMethodType));
    }

    @Override
    public BaseResponse<?> insert(EvaluationMethodRequest evaluationMethodRequest) {
        String methodName = evaluationMethodRequest.getMethodName();
        String methodCategory = evaluationMethodRequest.getMethodCategory();
        String methodType = evaluationMethodRequest.getMethodType();
        EvaluationMethod evaluationMethod = EvaluationMethod.builder()
                .methodName(methodName)
                .methodCategory(methodCategory)
                .methodType(methodType)
                .build();
        int insert = evaluationMethodMapper.insert(evaluationMethod);
        if(insert == 0){
            log.error("评测方法插入失败");
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }else{
            log.info("评测方法插入成功");
            return ResultUtils.success("插入成功");
        }
    }


}
