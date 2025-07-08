package com.example.secaicontainerengine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.secaicontainerengine.pojo.dto.result.EvaluationStatus;
import com.example.secaicontainerengine.pojo.entity.EvaluationResult;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface EvaluationResultMapper extends BaseMapper<EvaluationResult> {

    List<Map<String, Object>> getEvaluationDetailByModelId(@Param("modelId") Long modelId);

    void updateStatus(EvaluationStatus evaluationStatus);
}
