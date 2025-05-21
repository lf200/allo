package com.example.secaicontainerengine.service.modelEvaluation;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.common.ErrorCode;
import com.example.secaicontainerengine.config.SftpUploader;
import com.example.secaicontainerengine.exception.BusinessException;
import com.example.secaicontainerengine.mapper.ModelEvaluationMapper;
import com.example.secaicontainerengine.mapper.ModelMessageMapper;
import com.example.secaicontainerengine.pojo.dto.model.BusinessConfig;
import com.example.secaicontainerengine.pojo.dto.model.EvaluationConfig;
import com.example.secaicontainerengine.pojo.dto.model.ModelScore;
import com.example.secaicontainerengine.pojo.entity.ModelEvaluation;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.pojo.entity.SftpConnect;
import com.example.secaicontainerengine.service.container.ContainerService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@Slf4j
public class ModelEvaluationServiceImpl extends ServiceImpl<ModelEvaluationMapper, ModelEvaluation> implements ModelEvaluationService {

    // 用-1表示评测得分空值(即没有该评测项)
    final BigDecimal MISSING_FLAG = BigDecimal.valueOf(-1);

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


    @Override
    public void startEvaluationPod(ModelMessage modelMessage) throws Exception {
        // 根据不同的攻击类型来构造 Pod 的配置信息
        List<String> podYamlFiles = JSONUtil.toBean(modelMessage.getBusinessConfig(), BusinessConfig.class).getEvaluateMethods();

        List streams = containerService.initNew(modelMessage, podYamlFiles);

        // 创建nfs远程目录
        SftpConnect sftpConnect = sftpUploader.connectNfs();
        ChannelSftp sftpChannel = sftpConnect.getSftpChannel();
        Session session = sftpConnect.getSession();


        session.setConfig("LogLevel", "DEBUG");
        // 设置客户端的超时和心跳包
        session.setConfig("ServerAliveInterval", "60");  // 每60秒发送一个心跳包
        session.setConfig("ServerAliveCountMax", "5");   // 如果连续5次没有响应，则断开连接

        sftpChannel.connect();
        for (String podYamlFile : podYamlFiles) {
            String outputRemoteDir = nfsPath + File.separator + userData
                    + File.separator + modelMessage.getUserId()
                    + File.separator + modelMessage.getId()
                    + File.separator + evaluationData
                    + File.separator + podYamlFile
                    + File.separator + outputData;
            sftpUploader.createRemoteDirectory(sftpChannel, outputRemoteDir);
            String resultRemoteDir = nfsPath + File.separator + userData
                    + File.separator + modelMessage.getUserId()
                    + File.separator + modelMessage.getId()
                    + File.separator + evaluationData
                    + File.separator + podYamlFile
                    + File.separator + resultData;
            sftpUploader.createRemoteDirectory(sftpChannel, resultRemoteDir);
        }

        // 执行镜像相关sh脚本
        // 构造脚本路径
        String scriptPath = "/home/nfs/k8s/userData/" + modelMessage.getUserId() + "/" + modelMessage.getId() + "/modelData/imageOpe.sh";
        sftpUploader.shRemoteScript(session, scriptPath);

        sftpChannel.disconnect();
        session.disconnect();

        // 修改表状态
        // 根据模型id获取到模型评测表的对应记录
        QueryWrapper<ModelEvaluation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("modelId", modelMessage.getId());
        ModelEvaluation modelEvaluation = modelEvaluationMapper.selectOne(queryWrapper);
        modelEvaluation.setStatus("评测中");
        modelEvaluation.setUpdateTime(LocalDateTime.now());
        modelEvaluationMapper.updateById(modelEvaluation);

        modelMessage.setStatus(3);
        modelMessage.setUpdateTime(LocalDateTime.now());
        modelMessageMapper.updateById(modelMessage);


        // 使用 K8sClient 启动 Pod
        containerService.start(modelMessage.getUserId(), modelMessage.getId(), streams);


    }

    @Override
    public List<String> getEvaluationMethods(Long modelId) {
        ModelMessage modelMessage = modelMessageMapper.selectById(modelId);
        String businessConfigStr = modelMessage.getBusinessConfig();
        BusinessConfig businessConfig = JSONUtil.toBean(businessConfigStr, BusinessConfig.class);
        return businessConfig.getEvaluateMethods();
    }

    @Override
    public void updateModelScores(Long modelId, Map<String, BigDecimal> typeScoreMap) {

        // 1.根据模型id获取到模型评测表的对应记录
        QueryWrapper<ModelEvaluation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("modelId", modelId);
        ModelEvaluation modelEvaluation = modelEvaluationMapper.selectOne(queryWrapper);

        // 2.计算得分
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
        String modelScoreStr = JSONUtil.toJsonStr(modelScore);
        modelEvaluation.setModelScore(modelScoreStr);

        // 3.修改评测状态
        modelEvaluation.setStatus("成功");
        modelEvaluation.setUpdateTime(LocalDateTime.now());

        ModelMessage modelMessage = modelMessageMapper.selectById(modelId);
        modelMessage.setStatus(4);
        modelMessage.setUpdateTime(LocalDateTime.now());
        modelMessageMapper.updateById(modelMessage);

        // 4.更新数据表
        int updateCount = modelEvaluationMapper.updateById(modelEvaluation);
        if (updateCount != 1) {
            throw new RuntimeException("模型评测记录更新失败，modelId: " + modelId);
        }
    }

    @Override
    public void handleTimeout(Long methodId) {
        //todo 一个模型开启的所有的评测任务中在一个小时内有没有执行完的(超时)
        log.error("任务超时");

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
        List<String> evaluateMethods = businessConfig.getEvaluateMethods();
        // 检查 evaluateMethods 是否为空或 null，若为空则抛出异常
        if (evaluateMethods == null || evaluateMethods.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "没有选择任何攻击类型！");
        }
        // 直接将 evaluateMethods 中的内容添加到 podYamlFiles（假设 evaluateMethods 中的元素就是需要的文件标识）
        podYamlFiles.addAll(evaluateMethods);
        return podYamlFiles;
    }

}
