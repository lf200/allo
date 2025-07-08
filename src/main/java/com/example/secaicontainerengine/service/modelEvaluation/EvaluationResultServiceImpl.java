package com.example.secaicontainerengine.service.modelEvaluation;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.mapper.EvaluationResultMapper;
import com.example.secaicontainerengine.mapper.ModelEvaluationMapper;
import com.example.secaicontainerengine.pojo.dto.result.EvaluationStatus;
import com.example.secaicontainerengine.pojo.dto.result.PodResult;
import com.example.secaicontainerengine.pojo.entity.EvaluationResult;
import com.example.secaicontainerengine.pojo.entity.ModelEvaluation;
import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.GenerateReport;
import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.CleanAdv;
import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Robustness.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

    @Autowired
    private ModelEvaluationMapper modelEvaluationMapper;

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
            result.setUpdateTime(LocalDateTime.now());

            // 将更新后的记录同步到数据库
            boolean updateSuccess = evaluationResultMapper.updateById(result) > 0;
            if (!updateSuccess) {
                throw new RuntimeException("评测记录ID=" + id + " 得分更新失败");
            }
        }
    }

    @Override
    public GenerateReport getResultReport(Long modelId) {

        // 1.查询当前模型的所有评测记录（evaluation_result 表）
        List<EvaluationResult> resultList = getEvaluationResultsByModelId(modelId);
        if (resultList.isEmpty()) {
            throw new RuntimeException("模型 ID=" + modelId + " 无评测记录，无法计算得分");
        }
        QueryWrapper<ModelEvaluation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("modelId", modelId);
        ModelEvaluation modelEvaluation = modelEvaluationService.getOne(queryWrapper);

        // 2.根据评测输出计算得分
        GenerateReport generateReport = calculateEvaluationReportData(resultList);

        return generateReport;


    }


    // ！！展示使用！！
    // 所有的值都假设是归一化之后的得分，统一维度便于展示
    @Override
    public GenerateReport calculateEvaluationReportData(List<EvaluationResult> resultList) {
        if (resultList == null || resultList.isEmpty()) {
            return null;
        }

        // 遍历每条评测记录
        Map<String,PodResult> map = new HashMap<>();
        for (EvaluationResult result : resultList) {
            Long id = result.getId();
            // 从 result 字段提取原始结果
            PodResult rawResult = JSONUtil.toBean(result.getResult(), PodResult.class);
            if (rawResult == null) {
                throw new RuntimeException("评测记录ID=" + id + " 的result字段为空，无法计算得分");
            }
            Long evaluateMethodId = result.getEvaluateMethodId();
            if(evaluateMethodId == 1){
                map.put("fgsm", rawResult);
            }else if(evaluateMethodId == 2){
                map.put("pgd", rawResult);
            }
        }

        // 计算fgsm和pgd的攻击前后准确率的均值
        BigDecimal fgsmAccClean = map.get("fgsm").getAccClean();
        BigDecimal pgdAccClean = map.get("pgd").getAccClean();
        BigDecimal cleanSum = fgsmAccClean.add(pgdAccClean);
        BigDecimal cleanMean = cleanSum.divide(
                BigDecimal.valueOf(2),
                2,
                RoundingMode.HALF_UP
        );

        BigDecimal fgsmAccAdv = map.get("fgsm").getAccAdv();
        BigDecimal pgdAccAdv = map.get("pgd").getAccAdv();
        BigDecimal advSum = fgsmAccAdv.add(pgdAccAdv);
        BigDecimal advMean = advSum.divide(
                BigDecimal.valueOf(2),
                2,
                RoundingMode.HALF_UP
        );

        RobustnessMetricsCleanAdv robustnessMetricsCleanAdv = RobustnessMetricsCleanAdv.builder()
                .adversarialMetric(new CleanAdv(cleanMean.multiply(new BigDecimal(100)), advMean.multiply(new BigDecimal(100))))
                .gradientSensitivityMetric(new CleanAdv(new BigDecimal("65.5"), new BigDecimal("30.5")))
                .confidenceVarianceMetric(new CleanAdv(new BigDecimal("68.5"), new BigDecimal("29.5")))
                .build();

        RobustnessMetricsItem robustnessMetricsItem = RobustnessMetricsItem.builder()
                .adversarialMetric(new AdversarialMethodScore(new BigDecimal("40.5"), new BigDecimal("30.5")))
                .confidenceVarianceMetric(new AdversarialMethodScore(new BigDecimal("30.5"), new BigDecimal("20.5")))
                .gradientSensitivityMetric(new AdversarialMethodScore(new BigDecimal("40.5"), new BigDecimal("35.5")))
                .build();

        RobustnessMetricsMean robustnessMetricsMean = RobustnessMetricsMean.builder()
                .adversarialMetric(new BigDecimal("40.7"))
                .confidenceVarianceMetric(new BigDecimal("30.5"))
                .gradientSensitivityMetric(new BigDecimal("26.4"))
                .build();

        RobustnessEvaluation robustnessEvaluation = RobustnessEvaluation.builder()
                .totalScore(new BigDecimal("60.6"))
                .robustnessMetricsItem(robustnessMetricsItem)
                .robustnessMetricsCleanAdv(robustnessMetricsCleanAdv)
                .robustnessMetricsMean(robustnessMetricsMean)
                .build();

        GenerateReport generateReport = GenerateReport.builder()
                .totalScore(new BigDecimal("62.5"))
                .robustnessEvaluation(robustnessEvaluation)
                .build();
        return generateReport;
    }

    @Override
    public List<Map<String, Object>> getEvaluationDetailByModelId(Long modelId) {
        return evaluationResultMapper.getEvaluationDetailByModelId(modelId);
    }

    @Override
    @Transactional
    public void updateResult(Long modelId, Map<String, String> result, String resultColumn) {
        for (Map.Entry<String, String> entry : result.entrySet()) {
            modelEvaluationMapper.upsertJsonField(modelId, resultColumn, entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void updateStatus(EvaluationStatus evaluationStatus) {
        evaluationResultMapper.updateStatus(evaluationStatus);
    }
}
