package com.example.secaicontainerengine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.secaicontainerengine.pojo.entity.ModelEvaluation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface ModelEvaluationMapper extends BaseMapper<ModelEvaluation> {

    @Select("select createImageTime from model_evaluation where modelId=#{modelId}")
    Long getCreateImageTimeByModelId(Long modelId);

    void upsertJsonField(@Param("modelId") Long modelId,
                         @Param("column") String column,
                         @Param("key") String key,
                         @Param("value") Object value);

    @Select("select ${evaluateDimension}->>'$.${metric}' from model_evaluation where modelId=#{modelId}")
    String getJsonValue(String modelId, String evaluateDimension, String metric);

    @Select("select ${evaluateDimension} from model_evaluation where modelId=#{modelId}")
    String getResult(String modelId, String evaluateDimension);

    @Select("SELECT basicResult, interpretabilityResult, robustnessResult, safetyResult, generalizationResult FROM model_evaluation WHERE modelId = #{id}")
    Map<String, String> selectResults(@Param("id") Long modelId);
}
