package com.example.secaicontainerengine.service.modelEvaluation;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.mapper.EvaluationResultMapper;
import com.example.secaicontainerengine.pojo.dto.model.ModelScore;
import com.example.secaicontainerengine.pojo.dto.result.PodResult;
import com.example.secaicontainerengine.pojo.entity.EvaluationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.secaicontainerengine.util.PodUtil.calculateScoreFromResult;

@Service
@Slf4j
public class EvaluationResultServiceImpl extends ServiceImpl<EvaluationResultMapper, EvaluationResult> implements EvaluationResultService {


    @Lazy
    @Autowired
    private ModelEvaluationService modelEvaluationService;

    @Autowired
    private EvaluationMethodService evaluationMethodService;

    @Autowired
    private EvaluationResultMapper evaluationResultMapper;

    @Override
    public void calculateAndUpdateScores(Long modelId) {

        // 1.查询当前模型的所有评测记录（evaluation_result 表）
        List<EvaluationResult> resultList = getEvaluationResultsByModelId(modelId);
        if (resultList.isEmpty()) {
            throw new RuntimeException("模型 ID=" + modelId + " 无评测记录，无法计算得分");
        }

        // 2.根据评测输出计算得分
        updateEvaluationResultScore(resultList);

        // 3.获取所有评测记录的 evaluateMethodId，并查询对应的分类（Type）（evaluation_method 表）
        // 键是评测方法id，值是评测方法Type类别
        Map<Long, String> evaluateMethodTypeMap = evaluationMethodService.getEvaluationMethodTypeMap(resultList);

        // 4.按分类（Type）分组，计算每一类的得分(目前用平均值代替)
        // 键是Type类别，值是该类别的平均分
        Map<String, BigDecimal> typeScoreMap = calculateScoresByType(resultList, evaluateMethodTypeMap);

        // 5.更新模型的分类得分（示例：更新到 model_message 表）
        modelEvaluationService.updateModelScores(modelId, typeScoreMap);


    }

    @Override
    public List<EvaluationResult> getEvaluationResultsByModelId(Long modelId) {
        QueryWrapper<EvaluationResult> wrapper = new QueryWrapper<>();
        wrapper.eq("modelId", modelId);
        return evaluationResultMapper.selectList(wrapper);
    }

    @Override
    public Map<String, BigDecimal> calculateScoresByType(List<EvaluationResult> resultList,
                                                             Map<Long, String> evaluateMethodTypeMap) {
        // 用平均分代替

        // 按分类分组，累加得分（BigDecimal）和记录数
        // 分类总得分（BigDecimal）
        Map<String, BigDecimal> typeTotalScore = new HashMap<>();
        // 分类记录数
        Map<String, Integer> typeCount = new HashMap<>();

        for (EvaluationResult result : resultList) {
            Long evaluateMethodId = result.getEvaluateMethodId();
            if (evaluateMethodId == null) continue;

            String type = evaluateMethodTypeMap.get(evaluateMethodId);
            if (type == null) {
                throw new RuntimeException("evaluateMethodId=" + evaluateMethodId + " 未找到对应的分类");
            }

            BigDecimal score = result.getScore();
            // 跳过无效得分（null 或负数）
            if (score == null || score.compareTo(BigDecimal.ZERO) == -1) {
                continue;
            }

            // 累加总得分（使用 BigDecimal 的 add 方法）
            typeTotalScore.merge(
                    type,
                    score,
                    // 合并函数：总得分 + 当前得分
                    BigDecimal::add
            );

            // 累加记录数
            typeCount.merge(type, 1, Integer::sum);
        }

        // 计算平均分（总得分 / 记录数，保留 4 位小数，四舍五入）
        Map<String, BigDecimal> avgScoreMap = new HashMap<>();
        for (String type : typeTotalScore.keySet()) {
            BigDecimal total = typeTotalScore.get(type);
            Integer count = typeCount.get(type);

            // 避免除零异常（理论上 count >=1，因为 total 非空）
            if (count == 0) {
                throw new RuntimeException("分类=" + type + " 的记录数为 0，无法计算平均分");
            }

            // 转换为 BigDecimal 进行除法（保留 4 位小数，四舍五入）
            BigDecimal avg = total.divide(
                    // 记录数转为 BigDecimal
                    BigDecimal.valueOf(count),
                    // 保留 4 位小数
                    4,
                    // 四舍五入模式
                    BigDecimal.ROUND_HALF_UP
            );

            avgScoreMap.put(type, avg);
        }
        return avgScoreMap;
    }


    @Override
    public void updateEvaluationResultScore(List<EvaluationResult> resultList) {
        if (resultList == null || resultList.isEmpty()) {
            return; // 无记录，直接返回
        }

        // 遍历每条评测记录
        for (EvaluationResult result : resultList) {
            Long id = result.getId();

            // 从 result 字段提取原始结果
            PodResult rawResult = JSONUtil.toBean(result.getResult(), PodResult.class);
            if (rawResult == null) {
                throw new RuntimeException("评测记录ID=" + id + " 的result字段为空，无法计算得分");
            }

            // 根据 result 计算得分
            BigDecimal score = calculateScoreFromResult(rawResult);

            // 更新当前记录的 score 字段
            result.setScore(score);

            // 将更新后的记录同步到数据库
            boolean updateSuccess = evaluationResultMapper.updateById(result) > 0;
            if (!updateSuccess) {
                throw new RuntimeException("评测记录ID=" + id + " 得分更新失败");
            }
        }
    }
}
