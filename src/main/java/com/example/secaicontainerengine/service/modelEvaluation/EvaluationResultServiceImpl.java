package com.example.secaicontainerengine.service.modelEvaluation;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.mapper.EvaluationResultMapper;
import com.example.secaicontainerengine.mapper.ModelEvaluationMapper;
import com.example.secaicontainerengine.mapper.ModelMessageMapper;
import com.example.secaicontainerengine.pojo.dto.model.ModelConfig;
import com.example.secaicontainerengine.pojo.dto.model.ModelScore;
import com.example.secaicontainerengine.pojo.dto.result.EvaluationStatus;
import com.example.secaicontainerengine.pojo.dto.result.PodResult;
import com.example.secaicontainerengine.pojo.entity.EvaluationResult;
import com.example.secaicontainerengine.pojo.entity.ModelEvaluation;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.GenerateReport;
import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.CleanAdv;
import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.Report.Robustness.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static com.example.secaicontainerengine.util.PodUtil.calculateScoreFromResult;

@Service
@Slf4j
public class EvaluationResultServiceImpl extends ServiceImpl<EvaluationResultMapper, EvaluationResult> implements EvaluationResultService {

    @Autowired
    private ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private ModelEvaluationService modelEvaluationService;

    @Autowired
    private EvaluationMethodService evaluationMethodService;

    @Autowired
    private EvaluationResultMapper evaluationResultMapper;

    @Autowired
    private ModelEvaluationMapper modelEvaluationMapper;

    @Autowired
    private ModelMessageMapper modelMessageMapper;

    private static final List<String> RESULT = List.of("basicResult", "interpretabilityResult");

    /**
     * 计算并更新模型总分 & 最终状态
     * 关键修复：
     * 1) 只在所有维度完成后计算
     * 2) 有任何失败 -> modelEvaluation.status=失败
     * 3) 避免 RoundingMode 依赖
     */
    @Override
    @Transactional
    public void calculateAndUpdateScores(Long modelId) {

        // === 0. 先看所有单项任务状态 ===
        List<EvaluationResult> resultList = getEvaluationResultsByModelId(modelId);
        if (resultList == null || resultList.isEmpty()) {
            log.warn("模型 {} evaluation_result 为空，跳过总分计算", modelId);
            return;
        }

        boolean anyRunning = resultList.stream().anyMatch(r -> "评测中".equals(r.getStatus()));
        boolean anyFailed  = resultList.stream().anyMatch(r -> "失败".equals(r.getStatus()));

        // 只要还有评测中，就不做最终结算
        if (anyRunning) {
            log.info("模型 {} 仍有评测任务在运行中，暂不计算总分", modelId);
            return;
        }

        // === 1. 查询每个维度 JSON 结果 ===
        Map<String, String> resultMap = modelEvaluationMapper.selectResults(modelId);

        // === 1.1 获取任务类型 classification/detection ===
        ModelMessage modelMessage = modelMessageMapper.selectById(modelId);
        String task = "classification";
        if (modelMessage != null && modelMessage.getModelConfig() != null) {
            try {
                ModelConfig modelConfig = JSONUtil.toBean(modelMessage.getModelConfig(), ModelConfig.class);
                if (modelConfig != null && modelConfig.getTask() != null) {
                    task = modelConfig.getTask();
                }
            } catch (Exception e) {
                log.warn("解析 modelConfig 失败，使用默认任务类型 classification", e);
            }
        }

        // === 2. 计算每个维度得分 ===
        Map<String, Double> scoreMap = new HashMap<>();

        String basic = resultMap.get("basicResult");
        if (isNotEmptyJson(basic)) {
            scoreMap.put("basicResult", computeBasicScore(basic, task));
        }

        String interpretability = resultMap.get("interpretabilityResult");
        if (isNotEmptyJson(interpretability)) {
            scoreMap.put("interpretabilityResult", computeInterpretabilityScore(interpretability));
        }

        String robustness = resultMap.get("robustnessResult");
        if (isNotEmptyJson(robustness)) {
            scoreMap.put("robustnessResult", computeRobustnessScore(robustness, task));
        }

        String generalization = resultMap.get("generalizationResult");
        if (isNotEmptyJson(generalization)) {
            scoreMap.put("generalizationResult", computeGeneralizationScore(generalization, task));
        }

        String fairness = resultMap.get("fairnessResult");
        if (isNotEmptyJson(fairness)) {
            scoreMap.put("fairnessResult", computeFairnessScore(fairness, task));
        }

        String safety = resultMap.get("safetyResult");
        if (isNotEmptyJson(safety)) {
            scoreMap.put("safetyResult", computeSafetyScore(safety, task));
        }

        log.info("模型 {} 各维度得分: {}", modelId, scoreMap);

        // === 3. 计算最终得分 ===
        double finalScore = calculateFinalScore(scoreMap);
        ModelScore modelScore = ModelScore.builder()
                .totalEvaluate(BigDecimal.valueOf(finalScore))
                .build();

        // === 4. 更新模型最终状态 ===
        ModelEvaluation modelEvaluation = modelEvaluationMapper.selectOne(
                new QueryWrapper<ModelEvaluation>().eq("modelId", modelId)
        );
        if (modelEvaluation == null) {
            log.warn("模型 {} model_evaluation 不存在，跳过最终更新", modelId);
            return;
        }

        // 有失败则最终置失败，否则置成功
        if (anyFailed) {
            modelEvaluation.setStatus("失败");
        } else {
            modelEvaluation.setStatus("成功");
        }

        modelEvaluation.setModelScore(JSONUtil.toJsonStr(modelScore));
        modelEvaluation.setUpdateTime(LocalDateTime.now());
        modelEvaluationMapper.updateById(modelEvaluation);

        // model_message 状态：只有完全成功才置 4
        if (modelMessage != null) {
            if (anyFailed) {
                // 你系统里失败状态号如果不是 5，你按实际改
                modelMessage.setStatus(5);
            } else {
                modelMessage.setStatus(4);
            }
            modelMessage.setUpdateTime(LocalDateTime.now());
            modelMessageMapper.updateById(modelMessage);
        }
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

        Map<String, BigDecimal> typeTotalScore = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();

        for (EvaluationResult result : resultList) {
            Long evaluateMethodId = result.getEvaluateMethodId();
            if (evaluateMethodId == null) continue;

            String type = evaluateMethodTypeMap.get(evaluateMethodId);
            if (type == null) {
                throw new RuntimeException("evaluateMethodId=" + evaluateMethodId + " 未找到对应的分类");
            }

            BigDecimal score = result.getScore();
            if (score == null || score.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }

            typeTotalScore.merge(type, score, BigDecimal::add);
            typeCount.merge(type, 1, Integer::sum);
        }

        Map<String, BigDecimal> avgScoreMap = new HashMap<>();
        for (String type : typeTotalScore.keySet()) {
            BigDecimal total = typeTotalScore.get(type);
            Integer count = typeCount.getOrDefault(type, 0);
            if (count == 0) {
                throw new RuntimeException("分类=" + type + " 的记录数为 0，无法计算平均分");
            }

            BigDecimal avg = total.divide(BigDecimal.valueOf(count), 4, BigDecimal.ROUND_HALF_UP);
            avgScoreMap.put(type, avg);
        }
        return avgScoreMap;
    }

    @Override
    public void updateEvaluationResultScore(List<EvaluationResult> resultList) {
        if (resultList == null || resultList.isEmpty()) return;

        for (EvaluationResult result : resultList) {
            Long id = result.getId();
            PodResult rawResult = JSONUtil.toBean(result.getResult(), PodResult.class);
            if (rawResult == null) {
                throw new RuntimeException("评测记录ID=" + id + " 的result字段为空，无法计算得分");
            }

            BigDecimal score = calculateScoreFromResult(rawResult);
            result.setScore(score);
            result.setUpdateTime(LocalDateTime.now());

            boolean updateSuccess = evaluationResultMapper.updateById(result) > 0;
            if (!updateSuccess) {
                throw new RuntimeException("评测记录ID=" + id + " 得分更新失败");
            }
        }
    }

    @Override
    public GenerateReport getResultReport(Long modelId) {
        List<EvaluationResult> resultList = getEvaluationResultsByModelId(modelId);
        if (resultList.isEmpty()) {
            throw new RuntimeException("模型 ID=" + modelId + " 无评测记录，无法计算得分");
        }
        return calculateEvaluationReportData(resultList);
    }

    @Override
    public GenerateReport calculateEvaluationReportData(List<EvaluationResult> resultList) {
        if (resultList == null || resultList.isEmpty()) return null;

        Map<String, PodResult> map = new HashMap<>();
        for (EvaluationResult result : resultList) {
            Long id = result.getId();
            PodResult rawResult = JSONUtil.toBean(result.getResult(), PodResult.class);
            if (rawResult == null) {
                throw new RuntimeException("评测记录ID=" + id + " 的result字段为空，无法计算得分");
            }
            Long evaluateMethodId = result.getEvaluateMethodId();
            if (evaluateMethodId == 1) {
                map.put("fgsm", rawResult);
            } else if (evaluateMethodId == 2) {
                map.put("pgd", rawResult);
            }
        }

        BigDecimal fgsmAccClean = map.get("fgsm").getAccClean();
        BigDecimal pgdAccClean = map.get("pgd").getAccClean();
        BigDecimal cleanMean = fgsmAccClean.add(pgdAccClean)
                .divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP);

        BigDecimal fgsmAccAdv = map.get("fgsm").getAccAdv();
        BigDecimal pgdAccAdv = map.get("pgd").getAccAdv();
        BigDecimal advMean = fgsmAccAdv.add(pgdAccAdv)
                .divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP);

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

        return GenerateReport.builder()
                .totalScore(new BigDecimal("62.5"))
                .robustnessEvaluation(robustnessEvaluation)
                .build();
    }

    @Override
    public List<Map<String, Object>> getEvaluationDetailByModelId(Long modelId) {
        return evaluationResultMapper.getEvaluationDetailByModelId(modelId);
    }

    @Override
    @Transactional
    public void updateResult(Long modelId, Map<String, String> result, String resultColumn) {
        if (result.containsKey("robustness")) {
            parseAndStoreRobustness(modelId, resultColumn, result.get("robustness"));
            return;
        }
        for (Map.Entry<String, String> entry : result.entrySet()) {
            modelEvaluationMapper.upsertJsonField(modelId, resultColumn, entry.getKey(), entry.getValue());
        }
    }

    private void parseAndStoreRobustness(Long modelId, String resultColumn, String robustnessJson) {
        try {
            objectMapper.readTree(robustnessJson); // 仅校验
            modelEvaluationMapper.upsertJsonField(modelId, resultColumn, "robustness", robustnessJson);
        } catch (Exception e) {
            log.error("解析并存储 robustness JSON 失败", e);
            modelEvaluationMapper.upsertJsonField(modelId, resultColumn, "robustness", robustnessJson);
        }
    }

    @Override
    public void updateStatus(EvaluationStatus evaluationStatus) {
        evaluationResultMapper.updateStatus(evaluationStatus);
    }

    private boolean isNotEmptyJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ====== 下面所有 computeXXXScore / normalize / robustness 解析逻辑保持你原样不动 ======
    // 你贴的后半段我就不重复改写了，只保留接口/调用一致性。
    // （从 computeBasicScore 一直到 normalizeGeneralizationMetric 的代码请保持原实现）

    private double computeBasicScore(String result, String task) {
        // 1. 检查 result 是否为空
        if (result == null || result.trim().isEmpty() || "{}".equals(result.trim())) {
            return 0.0;
        }
        try {
            // 2. 第一次解析：解析整个 basicResult JSON 字符串
            JsonNode root = objectMapper.readTree(result);

            // 3. 根据任务类型选择不同的指标数组
            String[] keys;
            if ("detection".equals(task)) {
                // 目标检测任务：5个指标
                // map: 平均精度（mean Average Precision）
                // map_50: IoU阈值0.5时的平均精度
                // precision: 精确率
                // recall: 召回率
                // per_class_ap: 每个类别的平均精度（字典格式，需要特殊处理）
                keys = new String[]{"map5095", "map50", "precision", "recall", "per_class_ap"};
                log.debug("使用目标检测指标计算basic得分");
            } else {
                // 分类任务（默认）：4个指标
                // accuracy: 准确率
                // precision: 精确率
                // recall: 召回率
                // f1score: F1分数
                keys = new String[]{"accuracy", "precision", "recall", "f1score"};
                log.debug("使用分类指标计算basic得分");
            }

            // 4. 遍历指标数组，提取指标值并累加
            double total = 0.0;
            int count = 0;
            for (String key : keys) {
                if (root.has(key)) {
                    try {
                        JsonNode valueNode = root.get(key);
                        double value;

                        // 5. 特殊处理 per_class_ap 字段（仅目标检测任务）
                        if ("per_class_ap".equals(key)) {
                            // per_class_ap 在数据库中的格式：
                            // 内部计算阶段：字典对象 {"0": 0.73, "1": 0.65}
                            // 回传到后端时：被 json.dumps() 序列化成 JSON 字符串
                            // 数据库存储：per_class_ap 字段的值是 JSON 字符串 "{\"0\": 0.73, \"1\": 0.65}"
                            //
                            // 所以需要二次解析：
                            // 第一次解析（上面）：得到 per_class_ap 字段的值是字符串节点
                            // 第二次解析（这里）：把这个字符串解析成 JSON 对象，然后遍历所有值计算平均值

                            if (valueNode.isTextual()) {
                                // 获取 JSON 字符串内容
                                String perClassApJson = valueNode.asText();
                                try {
                                    // 二次解析：把 JSON 字符串解析成 JSON 对象
                                    JsonNode perClassApObject = objectMapper.readTree(perClassApJson);

                                    // 遍历对象的所有字段值，计算平均值
                                    // 键是类别ID（如 "0", "1"），值是浮点数（如 0.73, 0.65）
                                    double sum = 0.0;
                                    int objectCount = 0;
                                    Iterator<Map.Entry<String, JsonNode>> fields = perClassApObject.fields();
                                    while (fields.hasNext()) {
                                        Map.Entry<String, JsonNode> entry = fields.next();
                                        JsonNode fieldValue = entry.getValue();
                                        // 只累加数值类型的值
                                        if (fieldValue.isNumber()) {
                                            sum += fieldValue.asDouble();
                                            objectCount++;
                                        }
                                    }
                                    // 计算平均值
                                    if (objectCount > 0) {
                                        value = sum / objectCount;
                                    } else {
                                        // 如果没有有效值，跳过该指标
                                        log.warn("per_class_ap 对象为空或没有有效数值，跳过该指标");
                                        continue;
                                    }
                                } catch (Exception e) {
                                    // 二次解析失败，跳过该指标
                                    log.warn("per_class_ap 二次解析失败，跳过该指标: {}", e.getMessage());
                                    continue;
                                }
                            } else {
                                // 如果不是字符串类型，说明格式不对，跳过该指标
                                log.warn("per_class_ap 不是字符串类型，跳过该指标");
                                continue;
                            }
                        } else {
                            // 6. 处理普通指标（map, map_50, precision, recall, accuracy, f1score）
                            // 这些指标的值可能是数字或字符串（评测系统可能转成字符串发送）
                            // 需要兼容两种格式
                            if (valueNode.isNumber()) {
                                // 如果已经是数字类型，直接获取
                                value = valueNode.asDouble();
                            } else {
                                // 如果是字符串类型，先转成字符串再解析为数字
                                value = Double.parseDouble(valueNode.asText());
                            }
                        }

                        // 累加指标值
                        total += value;
                        count++;
                    } catch (NumberFormatException e) {
                        // 数字解析失败，记录警告但继续处理其他指标
                        log.warn("字段 {} 解析失败: {}", key, e.getMessage());
                    } catch (Exception e) {
                        // 其他异常，记录警告但继续处理其他指标
                        log.warn("字段 {} 处理失败: {}", key, e.getMessage());
                    }
                }
            }

            // 7. 计算所有有效指标的平均值
            return count > 0 ? total / count : 0.0;
        } catch (Exception e) {
            // JSON 解析失败，返回 0.0
            log.error("JSON 解析失败: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * 计算可解释性得分
     *
     * @param result 可解释性评测结果JSON字符串
     * @return 可解释性得分（0.0 ~ 1.0）
     */
    private double computeInterpretabilityScore(String result) {
        if (result == null || result.trim().isEmpty() || "{}".equals(result.trim())) {
            return 0.0;
        }

        try {
            JsonNode root = objectMapper.readTree(result);

            // 检查是否有 fidelity_summary 节点
            if (root.has("fidelity_summary")) {
                JsonNode fidelitySummary = root.get("fidelity_summary");

                // 提取 keep_mean 和 drop_mean
                // keep_mean: 保留重要特征时的模型输出一致性（越高越好，范围 0-1）
                // drop_mean: 删除重要特征后的模型输出变化（越低说明特征重要性识别越准，范围 0-1）
                Double keepMean = null;
                Double dropMean = null;

                if (fidelitySummary.has("keep_mean") && !fidelitySummary.get("keep_mean").isNull()) {
                    keepMean = fidelitySummary.get("keep_mean").asDouble();
                }

                if (fidelitySummary.has("drop_mean") && !fidelitySummary.get("drop_mean").isNull()) {
                    dropMean = fidelitySummary.get("drop_mean").asDouble();
                }

                // 计算综合得分
                double totalScore = 0.0;
                int validMetricsCount = 0;

                // keep_mean 越高越好，直接作为正向指标
                if (keepMean != null) {
                    totalScore += Math.max(0.0, Math.min(1.0, keepMean));
                    validMetricsCount++;
                }

                // drop_mean 越低越好，使用 (1 - drop_mean) 作为正向指标
                if (dropMean != null) {
                    double normalizedDropScore = Math.max(0.0, Math.min(1.0, 1.0 - dropMean));
                    totalScore += normalizedDropScore;
                    validMetricsCount++;
                }

                // 返回平均分
                return validMetricsCount > 0 ? totalScore / validMetricsCount : 0.0;
            }

            // 如果没有 fidelity_summary 节点，返回 0.0
            return 0.0;

        } catch (Exception e) {
            log.error("可解释性得分计算失败: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * 计算鲁棒性得分
     *
     * @param result 鲁棒性评测结果JSON字符串
     * @param task 任务类型："classification"（分类）或 "detection"（目标检测）
     * @return 鲁棒性得分（0.0 ~ 1.0）
     */
    private double computeRobustnessScore(String result, String task) {

        if (result == null || result.trim().isEmpty() || "{}".equals(result.trim())) {
            return 0.0;
        }
        try {
            JsonNode root = objectMapper.readTree(result);

            // 检测是否为扁平格式（新格式）
            // 扁平格式特征：有 map_drop_rate_xxx 或 performance_drop_rate_xxx 这样的键
            boolean isFlatFormat = false;
            Iterator<String> fieldNames = root.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (fieldName.startsWith("map_drop_rate_") ||
                        fieldName.startsWith("miss_rate_") ||
                        fieldName.startsWith("performance_drop_rate_") ||
                        fieldName.startsWith("perturbation_tolerance_")) {
                    isFlatFormat = true;
                    break;
                }
            }

            // 处理扁平格式（新格式）
            if (isFlatFormat) {
                log.debug("检测到扁平格式的 robustness 数据");
                if ("detection".equals(task)) {
                    return computeDetectionRobustnessScoreFromFlatFormat(root);
                } else {
                    return computeClassificationRobustnessScore(root);
                }
            }

            // 处理数组格式：{"adversarial": [...], "corruption": [...]}
            if (root.has("adversarial") || root.has("corruption")) {
                JsonNode adversarialNode = root.get("adversarial");
                JsonNode corruptionNode = root.get("corruption");

                // 如果 adversarial 或 corruption 是字符串类型，需要二次解析
                if ((adversarialNode != null && adversarialNode.isTextual()) ||
                        (corruptionNode != null && corruptionNode.isTextual())) {
                    log.debug("检测到字符串格式的 adversarial/corruption，进行二次解析");
                    root = parseRobustnessFields(root);
                }

                // 根据任务类型选择解析方式
                if ("detection".equals(task)) {
                    log.debug("使用目标检测任务的鲁棒性评测指标（数组格式）");
                    return computeDetectionRobustnessScore(root);
                } else {
                    log.debug("使用分类任务的鲁棒性评测指标");
                    return computeClassificationRobustnessScore(root);
                }
            }

            // 处理旧格式：{"robustness": "{\"adversarial\": [...]}"}（兼容性处理）
            if (root.has("robustness") && root.get("robustness").isTextual()) {
                String innerJson = root.get("robustness").asText();
                root = objectMapper.readTree(innerJson);
                log.debug("检测到旧的robustness包裹层格式，已解析内层JSON");

                if ("detection".equals(task) && (root.has("adversarial") || root.has("corruption"))) {
                    log.debug("使用目标检测任务的鲁棒性评测指标");
                    return computeDetectionRobustnessScore(root);
                }
            }

            // 分类任务或传统格式
            log.debug("使用分类任务的鲁棒性评测指标");
            return computeClassificationRobustnessScore(root);

        } catch (Exception e) {
            log.error("鲁棒性得分计算失败: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * 解析字符串形式的 adversarial 和 corruption 字段
     * 将 {"adversarial": "[...]", "corruption": "[...]"} 转换为
     *    {"adversarial": [...], "corruption": [...]}
     *
     * @param root 原始JsonNode
     * @return 解析后的JsonNode
     */
    private JsonNode parseRobustnessFields(JsonNode root) throws Exception {
        Map<String, Object> parsedMap = new HashMap<>();

        // 解析 adversarial 字段
        if (root.has("adversarial")) {
            JsonNode adversarialNode = root.get("adversarial");
            if (adversarialNode.isTextual()) {
                // 字符串类型，需要解析
                String adversarialJson = adversarialNode.asText();
                JsonNode parsedAdversarial = objectMapper.readTree(adversarialJson);
                parsedMap.put("adversarial", parsedAdversarial);
                log.debug("已解析 adversarial 字符串为数组");
            } else {
                // 已经是数组类型，直接使用
                parsedMap.put("adversarial", adversarialNode);
            }
        }

        // 解析 corruption 字段
        if (root.has("corruption")) {
            JsonNode corruptionNode = root.get("corruption");
            if (corruptionNode.isTextual()) {
                // 字符串类型，需要解析
                String corruptionJson = corruptionNode.asText();
                JsonNode parsedCorruption = objectMapper.readTree(corruptionJson);
                parsedMap.put("corruption", parsedCorruption);
                log.debug("已解析 corruption 字符串为数组");
            } else {
                // 已经是数组类型，直接使用
                parsedMap.put("corruption", corruptionNode);
            }
        }

        // 将Map转换回JsonNode
        return objectMapper.valueToTree(parsedMap);
    }

    /**
     * 计算分类任务的鲁棒性得分
     * 使用传统指标：adverr, advacc, acac, actc, mCE, RmCE
     */
    private double computeClassificationRobustnessScore(JsonNode root) {
        String[] keys = { "adverr", "advacc", "acac", "actc", "mCE", "RmCE" };
        double total = 0.0;
        int count = 0;
        for (String key : keys) {
            if (root.has(key)) {
                try {
                    double value = Double.parseDouble(root.get(key).asText());
                    total += value;
                    count++;
                } catch (NumberFormatException e) {
                    log.warn("字段 {} 解析失败: {}", key, e.getMessage());
                }
            }
        }
        return count > 0 ? total / count : 0.0;
    }

    /**
     * 从扁平格式计算目标检测任务的鲁棒性得分
     * 处理格式：{"map_drop_rate_fgsm_eps_0.001": "0.062", ...}
     *
     * @param root 扁平格式的鲁棒性评测结果
     * @return 鲁棒性综合得分（0.0 ~ 1.0）
     */
    private double computeDetectionRobustnessScoreFromFlatFormat(JsonNode root) {
        double totalScore = 0.0;
        int scoreCount = 0;

        try {
            // 按攻击方法分组提取 adversarial 指标
            Map<String, Map<String, Double>> adversarialMetrics = new HashMap<>();

            // 按腐败类型分组提取 corruption 指标
            Map<String, Map<String, Double>> corruptionMetrics = new HashMap<>();

            // 遍历所有字段
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                double value = field.getValue().asDouble();

                // 提取 adversarial 指标
                if (key.startsWith("map_drop_rate_")) {
                    String attackName = key.substring("map_drop_rate_".length());
                    adversarialMetrics.computeIfAbsent(attackName, k -> new HashMap<>())
                            .put("map_drop_rate", value);
                } else if (key.startsWith("miss_rate_")) {
                    String attackName = key.substring("miss_rate_".length());
                    adversarialMetrics.computeIfAbsent(attackName, k -> new HashMap<>())
                            .put("miss_rate", value);
                } else if (key.startsWith("false_detection_rate_")) {
                    String attackName = key.substring("false_detection_rate_".length());
                    adversarialMetrics.computeIfAbsent(attackName, k -> new HashMap<>())
                            .put("false_detection_rate", value);
                }

                // 提取 corruption 指标
                else if (key.startsWith("performance_drop_rate_")) {
                    String corruptionName = key.substring("performance_drop_rate_".length());
                    corruptionMetrics.computeIfAbsent(corruptionName, k -> new HashMap<>())
                            .put("performance_drop_rate", value);
                } else if (key.startsWith("perturbation_tolerance_")) {
                    String corruptionName = key.substring("perturbation_tolerance_".length());
                    corruptionMetrics.computeIfAbsent(corruptionName, k -> new HashMap<>())
                            .put("perturbation_tolerance", value);
                }
            }

            // 计算 adversarial 得分
            if (!adversarialMetrics.isEmpty()) {
                double advTotalScore = 0.0;
                int advCount = 0;

                for (Map.Entry<String, Map<String, Double>> entry : adversarialMetrics.entrySet()) {
                    Map<String, Double> metrics = entry.getValue();
                    double attackScore = 0.0;
                    int metricCount = 0;

                    if (metrics.containsKey("map_drop_rate")) {
                        attackScore += Math.max(0.0, 1.0 - metrics.get("map_drop_rate"));
                        metricCount++;
                    }
                    if (metrics.containsKey("miss_rate")) {
                        attackScore += Math.max(0.0, 1.0 - metrics.get("miss_rate"));
                        metricCount++;
                    }
                    if (metrics.containsKey("false_detection_rate")) {
                        attackScore += Math.max(0.0, 1.0 - metrics.get("false_detection_rate"));
                        metricCount++;
                    }

                    if (metricCount > 0) {
                        advTotalScore += attackScore / metricCount;
                        advCount++;
                    }
                }

                if (advCount > 0) {
                    totalScore += advTotalScore / advCount;
                    scoreCount++;
                    log.debug("对抗攻击得分（扁平格式）: {}", advTotalScore / advCount);
                }
            }

            // 计算 corruption 得分
            if (!corruptionMetrics.isEmpty()) {
                double corrTotalScore = 0.0;
                int corrCount = 0;

                for (Map.Entry<String, Map<String, Double>> entry : corruptionMetrics.entrySet()) {
                    Map<String, Double> metrics = entry.getValue();
                    double corruptionScore = 0.0;
                    int metricCount = 0;

                    if (metrics.containsKey("performance_drop_rate")) {
                        corruptionScore += Math.max(0.0, 1.0 - metrics.get("performance_drop_rate"));
                        metricCount++;
                    }
                    if (metrics.containsKey("perturbation_tolerance")) {
                        corruptionScore += Math.max(0.0, metrics.get("perturbation_tolerance"));
                        metricCount++;
                    }

                    if (metricCount > 0) {
                        corrTotalScore += corruptionScore / metricCount;
                        corrCount++;
                    }
                }

                if (corrCount > 0) {
                    totalScore += corrTotalScore / corrCount;
                    scoreCount++;
                    log.debug("腐败测试得分（扁平格式）: {}", corrTotalScore / corrCount);
                }
            }

            return scoreCount > 0 ? totalScore / scoreCount : 0.0;

        } catch (Exception e) {
            log.error("扁平格式鲁棒性得分计算失败: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * 计算目标检测任务的鲁棒性得分（数组格式）
     * 解析包含 adversarial 和 corruption 字段的鲁棒性评测结果
     *
     * @param root 鲁棒性评测结果的JSON根节点
     * @return 鲁棒性综合得分（0.0 ~ 1.0）
     */
    private double computeDetectionRobustnessScore(JsonNode root) {
        double totalScore = 0.0;
        int scoreCount = 0;

        // 1. 计算对抗攻击得分
        if (root.has("adversarial") && root.get("adversarial").isArray()) {
            double adversarialScore = computeAdversarialScore(root.get("adversarial"));
            if (adversarialScore >= 0) {
                totalScore += adversarialScore;
                scoreCount++;
                log.debug("对抗攻击得分: {}", adversarialScore);
            }
        }

        // 2. 计算腐败测试得分
        if (root.has("corruption") && root.get("corruption").isArray()) {
            double corruptionScore = computeCorruptionScore(root.get("corruption"));
            if (corruptionScore >= 0) {
                totalScore += corruptionScore;
                scoreCount++;
                log.debug("腐败测试得分: {}", corruptionScore);
            }
        }

        // 3. 返回平均得分
        return scoreCount > 0 ? totalScore / scoreCount : 0.0;
    }

    /**
     * 计算对抗攻击得分
     * 基于 map_drop_rate, miss_rate, false_detection_rate 计算
     * 指标值越低，得分越高
     *
     * @param adversarialArray 对抗攻击结果数组
     * @return 对抗攻击得分（0.0 ~ 1.0），解析失败返回 -1
     */
    private double computeAdversarialScore(JsonNode adversarialArray) {
        try {
            double totalScore = 0.0;
            int count = 0;

            for (JsonNode attack : adversarialArray) {
                double attackScore = 0.0;
                int metricCount = 0;

                // map_drop_rate: mAP下降率，越低越好，转换为得分 (1 - drop_rate)
                if (attack.has("map_drop_rate")) {
                    double mapDropRate = attack.get("map_drop_rate").asDouble();
                    attackScore += Math.max(0.0, 1.0 - mapDropRate);
                    metricCount++;
                }

                // miss_rate: 漏检率，越低越好，转换为得分 (1 - miss_rate)
                if (attack.has("miss_rate")) {
                    double missRate = attack.get("miss_rate").asDouble();
                    attackScore += Math.max(0.0, 1.0 - missRate);
                    metricCount++;
                }

                // false_detection_rate: 误检率，越低越好，转换为得分 (1 - fdr)
                if (attack.has("false_detection_rate")) {
                    double falseDetectionRate = attack.get("false_detection_rate").asDouble();
                    attackScore += Math.max(0.0, 1.0 - falseDetectionRate);
                    metricCount++;
                }

                // 计算当前攻击的平均得分
                if (metricCount > 0) {
                    totalScore += attackScore / metricCount;
                    count++;
                }
            }

            // 返回所有攻击的平均得分
            return count > 0 ? totalScore / count : -1;
        } catch (Exception e) {
            log.error("对抗攻击得分计算失败: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 计算腐败测试得分
     * 基于 performance_drop_rate 和 perturbation_tolerance 计算
     *
     * @param corruptionArray 腐败测试结果数组
     * @return 腐败测试得分（0.0 ~ 1.0），解析失败返回 -1
     */
    private double computeCorruptionScore(JsonNode corruptionArray) {
        try {
            double totalScore = 0.0;
            int count = 0;

            for (JsonNode corruption : corruptionArray) {
                double corruptionScore = 0.0;
                int metricCount = 0;

                // performance_drop_rate: 性能下降率，越低越好，转换为得分 (1 - drop_rate)
                if (corruption.has("performance_drop_rate")) {
                    double performanceDropRate = corruption.get("performance_drop_rate").asDouble();
                    corruptionScore += Math.max(0.0, 1.0 - performanceDropRate);
                    metricCount++;
                }

                // perturbation_tolerance: 扰动容忍度，越高越好，直接作为得分
                if (corruption.has("perturbation_tolerance")) {
                    double perturbationTolerance = corruption.get("perturbation_tolerance").asDouble();
                    corruptionScore += Math.max(0.0, perturbationTolerance);
                    metricCount++;
                }

                // 计算当前腐败的平均得分
                if (metricCount > 0) {
                    totalScore += corruptionScore / metricCount;
                    count++;
                }
            }

            // 返回所有腐败的平均得分
            return count > 0 ? totalScore / count : -1;
        } catch (Exception e) {
            log.error("腐败测试得分计算失败: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 计算安全性得分
     *
     * @param result 安全性评测结果JSON字符串
     * @param task 任务类型："classification"（分类）或 "detection"（目标检测）
     * @return 安全性得分（0.0 ~ 1.0）
     */
    private double computeSafetyScore(String result, String task) {
        if (result == null || result.trim().isEmpty() || "{}".equals(result.trim())) {
            return 0.0;
        }

        try {
            JsonNode root = objectMapper.readTree(result);
            double totalScore = 0.0;
            int validMetricsCount = 0;

            // 根据任务类型选择不同的指标
            if ("detection".equals(task)) {
                // 目标检测任务：使用 MIA 评测实际回传的字段
                // Python 实际发送：Accuracy, Precision, Recall, F1, TPR, FPR, AUC

                // 1. 使用 TPR (True Positive Rate)：攻击者正确识别训练集成员的比例
                // TPR 越高说明攻击越成功，模型越不安全
                if (root.has("TPR")) {
                    try {
                        double tpr = Double.parseDouble(root.get("TPR").asText());
                        // TPR 是攻击成功率，越低越安全，转换为安全得分
                        double normalizedValue = Math.max(0.0, 1.0 - tpr);
                        totalScore += normalizedValue;
                        validMetricsCount++;
                        log.debug("目标检测安全性指标 TPR: {}, 归一化得分: {}", tpr, normalizedValue);
                    } catch (NumberFormatException e) {
                        log.warn("字段 TPR 解析失败: {}", e.getMessage());
                    }
                }

                // 2. 使用 AUC (Area Under ROC Curve)：攻击模型的整体性能
                // AUC=0.5 表示随机猜测（攻击无效），AUC=1.0 表示完美攻击
                if (root.has("AUC")) {
                    try {
                        double auc = Double.parseDouble(root.get("AUC").asText());
                        // 将 AUC 转换为安全得分：score = 1 - (AUC - 0.5) * 2
                        // AUC=0.5 -> score=1.0 (完全安全)
                        // AUC=1.0 -> score=0.0 (完全不安全)
                        double normalizedValue = Math.max(0.0, 1.0 - (auc - 0.5) * 2);
                        totalScore += normalizedValue;
                        validMetricsCount++;
                        log.debug("目标检测安全性指标 AUC: {}, 归一化得分: {}", auc, normalizedValue);
                    } catch (NumberFormatException e) {
                        log.warn("字段 AUC 解析失败: {}", e.getMessage());
                    }
                }
            } else {
                // 分类任务（默认）：使用传统指标
                Map<String, Double> metrics = new LinkedHashMap<>();
                metrics.put("auc", 1.0);              // AUC指标，默认权重1.0
                metrics.put("tpr_at_fpr", 1.0);       // TPR@FPR指标，默认权重1.0
                metrics.put("attack_average_precision", 1.0); // 攻击平均精度，默认权重1.0

                // 遍历所有指标，计算加权分数
                for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                    String metricName = entry.getKey();
                    double weight = entry.getValue();

                    if (root.has(metricName)) {
                        try {
                            double rawValue = Double.parseDouble(root.get(metricName).asText());
                            double normalizedValue = normalizeMetric(metricName, rawValue);
                            totalScore += normalizedValue * weight;
                            validMetricsCount++;
                        } catch (NumberFormatException e) {
                            log.warn("字段 {} 解析失败: {}", metricName, e.getMessage());
                        }
                    }
                }
            }

            // 如果有有效指标，返回加权平均分；否则返回0
            return validMetricsCount > 0 ? totalScore / validMetricsCount : 0.0;
        } catch (Exception e) {
            log.error("安全性得分计算失败: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    // 对不同指标进行归一化处理，将"值越大安全性越差"的指标转换为"值越大安全性越好"
    private double normalizeMetric(String metricName, double rawValue) {
        switch (metricName) {
            case "auc":
                // AUC通常在0.8左右，越大安全性越差，转换为1-rawValue
                // 假设最坏情况AUC=1.0，最好情况AUC=0.5
                return Math.max(0.0, 1.0 - rawValue) * 2; // 缩放至[0,1]

            case "tpr_at_fpr":
                // TPR@FPR通常在0.05左右，越大安全性越差，转换为1-rawValue/0.1
                // 假设最坏情况TPR@FPR=0.1，最好情况TPR@FPR=0
                return Math.max(0.0, 1.0 - (rawValue / 0.1)); // 缩放至[0,1]

            case "attack_average_precision":
                // 攻击平均精度通常在0.9左右，越大安全性越差，转换为1-rawValue
                // 假设最坏情况attack_average_precision=1.0，最好情况=0
                return Math.max(0.0, 1.0 - rawValue); // 缩放至[0,1]

            default:
                // 对于未知指标，默认返回原始值（通常不应该发生）
                return rawValue;
        }
    }

    /**
     * 根据公平性指标计算0-1之间的综合公平性分数
     *
     * @param result 包含公平性指标的JSON字符串
     * @param task 任务类型："classification"（分类）或 "detection"（目标检测）
     * @return 公平性分数，范围从0（最不公平）到1（完全公平）
     */
    public double computeFairnessScore(String result, String task) {
        if (result == null || result.trim().isEmpty() || "{}".equals(result.trim())) {
            return 0.0;
        }

        try {
            JsonNode root = objectMapper.readTree(result);
            double totalScore = 0.0;
            int metricCount = 0;

            // 根据任务类型选择不同的指标
            if ("detection".equals(task)) {
                // 目标检测任务：使用 performance_gap（性能极差）
                if (root.has("performance_gap")) {
                    try {
                        double rawValue = root.get("performance_gap").asDouble();
                        // performance_gap 是性能差距，越小越公平，转换为公平得分
                        // 假设 performance_gap 在 [0, 1] 范围内，0表示完全公平，1表示完全不公平
                        double normalizedValue = Math.max(0.0, 1.0 - rawValue);
                        totalScore += normalizedValue;
                        metricCount++;
                        log.debug("目标检测公平性指标 performance_gap: {}, 归一化得分: {}", rawValue, normalizedValue);
                    } catch (Exception e) {
                        log.warn("字段 performance_gap 解析失败: {}", e.getMessage());
                    }
                }
            } else {
                // 分类任务（默认）：使用传统指标
                // 计算每个指标的公平性分数并累加
                if (root.has("spd")) {
                    double spd = root.get("spd").asDouble();
                    totalScore += calculateSPDScore(spd);
                    metricCount++;
                }

                if (root.has("dir")) {
                    double dir = root.get("dir").asDouble();
                    totalScore += calculateDIRScore(dir);
                    metricCount++;
                }

                if (root.has("eod")) {
                    double eod = root.get("eod").asDouble();
                    totalScore += calculateEODScore(eod);
                    metricCount++;
                }

                if (root.has("aod")) {
                    double aod = root.get("aod").asDouble();
                    totalScore += calculateAODScore(aod);
                    metricCount++;
                }

                if (root.has("consistency")) {
                    double consistency = root.get("consistency").asDouble();
                    totalScore += consistency; // 一致性已经是0-1之间的分数
                    metricCount++;
                }
            }

            // 如果没有有效指标，返回0
            return metricCount > 0 ? totalScore / metricCount : 0.0;

        } catch (Exception e) {
            log.error("公平性得分计算失败: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    /**
     * 计算统计 parity difference (SPD) 的公平性分数
     * SPD越接近0越公平
     */
    private double calculateSPDScore(double spd) {
        // 将SPD转换为0-1之间的分数，越接近0分数越高
        double absSPD = Math.abs(spd);
        // 假设SPD在[-0.5, 0.5]范围内，超过此范围的分数为0
        return Math.max(0.0, 1.0 - (absSPD * 2));
    }

    /**
     * 计算 disparate impact ratio (DIR) 的公平性分数
     * DIR越接近1越公平
     */
    private double calculateDIRScore(double dir) {
        // DIR应该接近1，低于0.8或高于1.2被认为不公平
        if (dir <= 0) {
            return 0.0;
        }

        double score;
        if (dir >= 1) {
            // DIR >=1 的情况
            score = 1.0 / Math.max(1.0, dir);
        } else {
            // DIR <1 的情况
            score = dir;
        }

        // 将分数限制在0-1范围内
        return Math.min(1.0, Math.max(0.0, score));
    }

    /**
     * 计算 equal opportunity difference (EOD) 的公平性分数
     * EOD越接近0越公平
     */
    private double calculateEODScore(double eod) {
        // 将EOD转换为0-1之间的分数，越接近0分数越高
        double absEOD = Math.abs(eod);
        // 假设EOD在[-0.5, 0.5]范围内，超过此范围的分数为0
        return Math.max(0.0, 1.0 - (absEOD * 2));
    }

    /**
     * 计算 average odds difference (AOD) 的公平性分数
     * AOD越接近0越公平
     */
    private double calculateAODScore(double aod) {
        // 将AOD转换为0-1之间的分数，越接近0分数越高
        double absAOD = Math.abs(aod);
        // 假设AOD在[-0.5, 0.5]范围内，超过此范围的分数为0
        return Math.max(0.0, 1.0 - (absAOD * 2));
    }

    /**
     * 计算泛化性得分
     *
     * @param result 泛化性评测结果JSON字符串
     * @param task 任务类型："classification"（分类）或 "detection"（目标检测）
     * @return 泛化性得分（0.0 ~ 1.0）
     */
    private double computeGeneralizationScore(String result, String task) {
        if (result == null || result.trim().isEmpty() || "{}".equals(result.trim())) {
            return 0.0;
        }

        try {
            JsonNode root = objectMapper.readTree(result);
            double totalScore = 0.0;
            int validMetricsCount = 0;

            // 根据任务类型选择不同的指标
            if ("detection".equals(task)) {
                // 目标检测任务：使用 drop_ratio_xx（跨数据集性能下降率）
                // Python 实际发送：source_map_50, target_map_50, drop_ratio_50
                // 可能有多个 IoU 阈值，如 drop_ratio_50, drop_ratio_75 等

                Iterator<String> fieldNames = root.fieldNames();
                while (fieldNames.hasNext()) {
                    String fieldName = fieldNames.next();

                    // 处理所有 drop_ratio 开头的字段
                    if (fieldName.startsWith("drop_ratio_")) {
                        try {
                            double rawValue = Double.parseDouble(root.get(fieldName).asText());
                            // drop_ratio 是性能下降率，越小泛化性越好
                            // 范围 [0, 1]：0表示完美泛化（无性能下降），1表示完全不泛化
                            double normalizedValue = Math.max(0.0, 1.0 - rawValue);
                            totalScore += normalizedValue;
                            validMetricsCount++;
                            log.debug("目标检测泛化性指标 {}: {}, 归一化得分: {}", fieldName, rawValue, normalizedValue);
                        } catch (NumberFormatException e) {
                            log.warn("字段 {} 解析失败: {}", fieldName, e.getMessage());
                        }
                    }
                }
            } else {
                // 分类任务（默认）：使用传统指标
                // 定义需要评估的泛化性指标及其权重
                Map<String, Double> metrics = new LinkedHashMap<>();
                metrics.put("msp", 1.0);              // 平均MSP，默认权重1.0
                metrics.put("entropy", 1.0);          // 平均预测熵，默认权重1.0
                metrics.put("rademacher", 1.0);       // Rademacher复杂度，默认权重1.0

                // 遍历所有指标，计算加权分数
                for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                    String metricName = entry.getKey();
                    double weight = entry.getValue();

                    if (root.has(metricName)) {
                        try {
                            double rawValue = Double.parseDouble(root.get(metricName).asText());
                            double normalizedValue = normalizeGeneralizationMetric(metricName, rawValue);
                            totalScore += normalizedValue * weight;
                            validMetricsCount++;
                        } catch (NumberFormatException e) {
                            log.warn("字段 {} 解析失败: {}", metricName, e.getMessage());
                        }
                    }
                }
            }

            // 如果有有效指标，返回加权平均分；否则返回0
            return validMetricsCount > 0 ? totalScore / validMetricsCount : 0.0;
        } catch (Exception e) {
            log.error("泛化性得分计算失败: {}", e.getMessage(), e);
            return 0.0;
        }
    }

    // 对不同指标进行归一化处理，将原始值映射到0-1区间（值越大泛化性越好）
    private double normalizeGeneralizationMetric(String metricName, double rawValue) {
        switch (metricName) {
            case "msp":
                // MSP在0.5(欠拟合)到0.9(过拟合)之间，转换为0-1区间
                // 公式: (raw - 0.5) / (0.9 - 0.5)
                return Math.min(Math.max((rawValue - 0.5) / 0.4, 0.0), 1.0);

            case "entropy":
                // 熵在0.5(过拟合)到2.0(欠拟合)之间，峰值约1.0，使用高斯函数归一化
                // 公式: exp(-0.5 * ((x-1.0)/0.5)^2)
                double peak = 1.0;
                double sigma = 0.5;
                return Math.exp(-0.5 * Math.pow((rawValue - peak) / sigma, 2));

            case "rademacher":
                // Rademacher复杂度在-0.1(好)到0.1(差)之间，转换为0-1区间
                // 公式: 1.0 - (raw + 0.1) / 0.2
                return Math.max(1.0 - (rawValue + 0.1) / 0.2, 0.0);

            default:
                // 未知指标，返回0
                return 0.0;
        }
    }
    /**
     * 最终得分：对所有“有结果(>=0)”的维度做平均
     * 关键修复：0 分也算有效结果，不再被过滤掉
     */
    public static double calculateFinalScore(Map<String, Double> scoreMap) {
        if (scoreMap == null || scoreMap.isEmpty()) return 0.0;

        double total = 0.0;
        int count = 0;
        for (double score : scoreMap.values()) {
            if (score >= 0.0) {
                total += score;
                count++;
            }
        }
        return count > 0 ? total / count : 0.0;
    }
}
