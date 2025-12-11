package com.example.secaicontainerengine.service.modelEvaluation;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.common.ErrorCode;
import com.example.secaicontainerengine.config.SftpUploader;
import com.example.secaicontainerengine.exception.BusinessException;
import com.example.secaicontainerengine.mapper.*;
import com.example.secaicontainerengine.pojo.dto.model.BusinessConfig;
import com.example.secaicontainerengine.pojo.dto.model.ModelScore;
import com.example.secaicontainerengine.pojo.entity.*;
import com.example.secaicontainerengine.service.container.ContainerService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ModelEvaluationServiceImpl
        extends ServiceImpl<ModelEvaluationMapper, ModelEvaluation>
        implements ModelEvaluationService {

    // 用-1表示评测得分空值(即没有该评测项)
    private static final BigDecimal MISSING_FLAG = BigDecimal.valueOf(-1);

    // ====== 本地轻量锁：按 modelId 防止并发汇总 ======
    private static final Map<Long, Object> MODEL_LOCKS = new ConcurrentHashMap<>();

    private Object lockOf(Long modelId) {
        return MODEL_LOCKS.computeIfAbsent(modelId, k -> new Object());
    }

    @Autowired
    private KubernetesClient k8sClient;

    @Autowired
    private ContainerService containerService;

    @Autowired
    @Lazy
    private SftpUploader sftpUploader;

    @Value("${nfs.rootPath}")
    private String nfsPath;

    @Value("${nfs.userData}")
    private String userData;

    @Value("${nfs.outputData}")
    private String outputData;

    @Value("${nfs.resultData}")
    private String resultData;

    @Value("${nfs.evaluationData}")
    private String evaluationData;

    @Autowired
    private ModelEvaluationMapper modelEvaluationMapper;

    @Autowired
    private ModelMessageMapper modelMessageMapper;

    @Autowired
    private EvaluationResultMapper evaluationResultMapper;

    @Autowired
    private EvaluationMethodMapper evaluationMethodMapper;

    @Override
    public void startEvaluationPod(ModelMessage modelMessage) throws Exception {

        Long modelId = modelMessage.getId();

        synchronized (lockOf(modelId)) {
            // ====== 防重复启动：成功/评测中就别再启动 ======
            ModelEvaluation existEval = modelEvaluationMapper.selectOne(
                    new QueryWrapper<ModelEvaluation>().eq("modelId", modelId)
            );
            if (existEval != null) {
                String st = existEval.getStatus();
                if ("评测中".equals(st)) {
                    log.warn("模型 {} 已在评测中，忽略重复启动", modelId);
                    return;
                }
                if ("成功".equals(st)) {
                    log.warn("模型 {} 已评测成功，忽略重复启动", modelId);
                    return;
                }
            }

            // ====== 1. 获取维度 ======
            List<BusinessConfig.EvaluationDimensionConfig> evaluateMethods =
                    JSONUtil.toBean(modelMessage.getBusinessConfig(), BusinessConfig.class).getEvaluateMethods();
            List<String> dimensions = evaluateMethods.stream()
                    .map(BusinessConfig.EvaluationDimensionConfig::getDimension)
                    .collect(Collectors.toList());

            List streams = containerService.initNew(modelMessage, dimensions);

            // ====== 2. 创建 NFS 目录 ======
            SftpConnect sftpConnect = sftpUploader.connectNfs();
            ChannelSftp sftpChannel = sftpConnect.getSftpChannel();
            Session session = sftpConnect.getSession();

            session.setConfig("LogLevel", "DEBUG");
            session.setConfig("ServerAliveInterval", "60");
            session.setConfig("ServerAliveCountMax", "5");

            sftpChannel.connect();
            for (String podYamlFile : dimensions) {
                String outputRemoteDir = nfsPath + "/" + userData
                        + "/" + modelMessage.getUserId()
                        + "/" + modelMessage.getId()
                        + "/" + evaluationData
                        + "/" + podYamlFile
                        + "/" + outputData;
                sftpUploader.createRemoteDirectory(sftpChannel, outputRemoteDir);

                String resultRemoteDir = nfsPath + "/" + userData
                        + "/" + modelMessage.getUserId()
                        + "/" + modelMessage.getId()
                        + "/" + evaluationData
                        + "/" + podYamlFile
                        + "/" + resultData;
                sftpUploader.createRemoteDirectory(sftpChannel, resultRemoteDir);
            }

            // ====== 3. 构建镜像 ======
            Instant scriptStartTime = Instant.now();
            String scriptPath = "/home/xd-1/k8s/userData/"
                    + modelMessage.getUserId() + "/" + modelMessage.getId()
                    + "/modelData/imageOpe.sh";
            sftpUploader.shRemoteScript(session, scriptPath);

            sftpChannel.disconnect();
            session.disconnect();

            long scriptDurationMs = Duration.between(scriptStartTime, Instant.now()).toMillis();
            log.info("镜像构建完成，耗时：{}ms", scriptDurationMs);

            // ====== 4. 总表状态：置为评测中（只允许初始/失败 -> 评测中） ======
            ModelEvaluation modelEvaluation = modelEvaluationMapper.selectOne(
                    new QueryWrapper<ModelEvaluation>().eq("modelId", modelId)
            );
            if (modelEvaluation != null) {
                String oldStatus = modelEvaluation.getStatus();
                if (!"成功".equals(oldStatus)) {
                    modelEvaluation.setStatus("评测中");
                }
                modelEvaluation.setCreateImageTime(scriptDurationMs);
                modelEvaluation.setUpdateTime(LocalDateTime.now());
                modelEvaluationMapper.updateById(modelEvaluation);
            }

            modelMessage.setStatus(3);
            modelMessage.setUpdateTime(LocalDateTime.now());
            modelMessageMapper.updateById(modelMessage);

            // ====== 5. 启动 Pods ======
            containerService.start(modelMessage.getUserId(), modelId, streams);
        }
    }

    @Override
    public List<String> getEvaluationMethods(Long modelId) {
        ModelMessage modelMessage = modelMessageMapper.selectById(modelId);
        BusinessConfig businessConfig = JSONUtil.toBean(modelMessage.getBusinessConfig(), BusinessConfig.class);
        return businessConfig.getEvaluateMethods()
                .stream()
                .map(BusinessConfig.EvaluationDimensionConfig::getDimension)
                .collect(Collectors.toList());
    }

    /**
     * 核心修复：只有当所有子评测都成功，才汇总并把总评测设为成功。
     * 否则只更新分项，不动总状态。
     */
    @Override
    public void updateModelScores(Long modelId, Map<String, BigDecimal> typeScoreMap) {

        synchronized (lockOf(modelId)) {

            // 1. 查总评测记录
            ModelEvaluation modelEvaluation = modelEvaluationMapper.selectOne(
                    new QueryWrapper<ModelEvaluation>().eq("modelId", modelId)
            );
            if (modelEvaluation == null) {
                log.warn("model_evaluation 不存在，modelId={}", modelId);
                return;
            }

            // 如果已经成功，不再回写（防止成功后被覆盖）
            if ("成功".equals(modelEvaluation.getStatus())) {
                log.info("模型 {} 已成功，不再重复计算总分", modelId);
                return;
            }

            // 2. 以 DB 为准补全 typeScoreMap（可选）
            mergeScoresFromResults(modelId, typeScoreMap);

            // 3. 判断子任务是否全部成功
            boolean anyRunning = evaluationResultMapper.exists(
                    new LambdaQueryWrapper<EvaluationResult>()
                            .eq(EvaluationResult::getModelId, modelId)
                            .eq(EvaluationResult::getStatus, "评测中")
            );
            boolean anyFailed = evaluationResultMapper.exists(
                    new LambdaQueryWrapper<EvaluationResult>()
                            .eq(EvaluationResult::getModelId, modelId)
                            .eq(EvaluationResult::getStatus, "失败")
            );

            if (anyRunning || anyFailed) {
                // 子任务没结束：只更新已计算分项，不动总状态
                String partialScoreStr = JSONUtil.toJsonStr(typeScoreMap);
                modelEvaluation.setModelScore(partialScoreStr);
                modelEvaluation.setUpdateTime(LocalDateTime.now());
                modelEvaluationMapper.updateById(modelEvaluation);
                log.info("模型 {} 子任务未全部结束，暂不汇总总分", modelId);
                return;
            }

            // 4. 子任务全部成功：开始汇总总分
            BigDecimal whiteBoxScore = fillMissingScore(typeScoreMap, "whiteBoxEvaluate");
            BigDecimal blackBoxScore = fillMissingScore(typeScoreMap, "blackBoxEvaluate");
            BigDecimal backdoorScore = fillMissingScore(typeScoreMap, "backdoorEvaluate");

            BigDecimal adversarialScore = calculateAverage(whiteBoxScore, blackBoxScore);
            BigDecimal totalScore = calculateAverage(adversarialScore, backdoorScore);

            ModelScore modelScore = ModelScore.builder()
                    .whiteBoxEvaluate(whiteBoxScore)
                    .blackBoxEvaluate(blackBoxScore)
                    .backdoorEvaluate(backdoorScore)
                    .adversarialEvaluate(adversarialScore)
                    .totalEvaluate(totalScore)
                    .build();

            modelEvaluation.setModelScore(JSONUtil.toJsonStr(modelScore));
            modelEvaluation.setStatus("成功");
            modelEvaluation.setUpdateTime(LocalDateTime.now());

            // 5. model_message 状态同步成功
            ModelMessage modelMessage = modelMessageMapper.selectById(modelId);
            if (modelMessage != null && modelMessage.getStatus() != 4) {
                modelMessage.setStatus(4);
                modelMessage.setUpdateTime(LocalDateTime.now());
                modelMessageMapper.updateById(modelMessage);
            }

            int updateCount = modelEvaluationMapper.updateById(modelEvaluation);
            if (updateCount != 1) {
                throw new RuntimeException("模型评测记录更新失败，modelId: " + modelId);
            }

            log.info("模型 {} 所有子任务成功，已汇总总分 total={}", modelId, totalScore);
        }
    }

    /**
     * 可选：从 evaluation_result 表按方法名汇总到 map。
     * 如果你外部已经算好 map，可删掉此方法调用。
     */
    private void mergeScoresFromResults(Long modelId, Map<String, BigDecimal> typeScoreMap) {
        List<EvaluationResult> results = evaluationResultMapper.selectList(
                new LambdaQueryWrapper<EvaluationResult>().eq(EvaluationResult::getModelId, modelId)
        );
        if (results == null || results.isEmpty()) return;

        // evaluateMethodId -> methodName
        Map<Long, String> methodNameMap = new HashMap<>();
        for (EvaluationResult r : results) {
            if (!methodNameMap.containsKey(r.getEvaluateMethodId())) {
                EvaluationMethod m = evaluationMethodMapper.selectById(r.getEvaluateMethodId());
                if (m != null) methodNameMap.put(r.getEvaluateMethodId(), m.getMethodName());
            }
        }

        for (EvaluationResult r : results) {
            String methodName = methodNameMap.get(r.getEvaluateMethodId());
            if (methodName == null) continue;
            if (r.getScore() == null) continue;

            try {
                BigDecimal score = new BigDecimal(r.getScore().toString());

                // 这里按你的业务自行映射：
                // 白盒 / 黑盒 / 后门 评测方法名示例
                if (methodName.contains("white") || methodName.contains("whiteBox")) {
                    typeScoreMap.put("whiteBoxEvaluate", score);
                } else if (methodName.contains("black") || methodName.contains("blackBox")) {
                    typeScoreMap.put("blackBoxEvaluate", score);
                } else if (methodName.contains("backdoor")) {
                    typeScoreMap.put("backdoorEvaluate", score);
                }
            } catch (Exception ignore) {}
        }
    }

    @Override
    public void handleTimeout(Long methodId) {
        log.error("任务超时 methodId={}", methodId);
    }

    private BigDecimal fillMissingScore(Map<String, BigDecimal> scoreMap, String scoreKey) {
        BigDecimal score = scoreMap.get(scoreKey);
        if (score == null) {
            score = MISSING_FLAG;
            scoreMap.put(scoreKey, score);
        }
        return score;
    }

    private BigDecimal calculateAverage(BigDecimal a, BigDecimal b) {
        boolean aValid = a.compareTo(MISSING_FLAG) != 0;
        boolean bValid = b.compareTo(MISSING_FLAG) != 0;

        if (!aValid && !bValid) {
            return MISSING_FLAG;
        } else if (aValid && bValid) {
            return a.add(b).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        } else {
            return aValid ? a : b;
        }
    }

    public List<String> getPodYamlFile(BusinessConfig businessConfig) {
        List<String> podYamlFiles = new ArrayList<>();
        List<String> evaluateMethods = businessConfig.getEvaluateMethods()
                .stream()
                .map(BusinessConfig.EvaluationDimensionConfig::getDimension)
                .toList();

        if (evaluateMethods == null || evaluateMethods.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "没有选择任何攻击类型！");
        }
        podYamlFiles.addAll(evaluateMethods);
        return podYamlFiles;
    }

    @Override
    public Long getCreateImageTimeByModelId(Long modelId) {
        return modelEvaluationMapper.getCreateImageTimeByModelId(modelId);
    }

    @Override
    public String getJsonValue(String modelId, String evaluateDimension, String metric) {
        return modelEvaluationMapper.getJsonValue(modelId, evaluateDimension, metric);
    }

    @Override
    public String getResult(String modelId, String evaluateDimension) {
        return modelEvaluationMapper.getResult(modelId, evaluateDimension);
    }

    @Override
    public String getScoreByModelId(String modelId) {
        return modelEvaluationMapper.getModelScoreByModelId(modelId);
    }
}
