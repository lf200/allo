package com.example.secaicontainerengine.service.modelEvaluation;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.common.ErrorCode;
import com.example.secaicontainerengine.config.SftpUploader;
import com.example.secaicontainerengine.exception.BusinessException;
import com.example.secaicontainerengine.mapper.ModelMessageMapper;
import com.example.secaicontainerengine.pojo.dto.model.BusinessConfig;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.pojo.entity.SftpConnect;
import com.example.secaicontainerengine.service.container.ContainerService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


@Service
@Slf4j
public class ModelEvaluationServiceImpl extends ServiceImpl<ModelMessageMapper, ModelMessage> implements ModelEvaluationService {

    @Autowired
    private KubernetesClient k8sClient;

    @Autowired
    private ContainerService containerService;

    @Autowired
    @Lazy
    private SftpUploader sftpUploader;

    @Value("${nfs.rootPath}")
    private String nfsPath;


    public void startEvaluationPod(ModelMessage modelMessage) throws Exception {
        // 根据不同的攻击类型来构造 Pod 的配置信息
        List<String> podYamlFiles = getPodYamlFile(JSONUtil.toBean(modelMessage.getBusinessConfig(), BusinessConfig.class));

        List streams = containerService.initNew(modelMessage, podYamlFiles, null);

        // 创建nfs远程目录
        SftpConnect sftpConnect = sftpUploader.connectNfs();
        ChannelSftp sftpChannel = sftpConnect.getSftpChannel();
        Session session = sftpConnect.getSession();
        sftpChannel.connect();
        for (String podYamlFile : podYamlFiles) {
            String remoteDir = nfsPath + File.separator + "model_data" + File.separator + modelMessage.getUserId()
                    + File.separator + modelMessage.getId() + File.separator + podYamlFile;
            sftpUploader.createRemoteDirectory(sftpChannel, remoteDir);
        }
        sftpChannel.disconnect();
        session.disconnect();

        // 使用 K8sClient 启动 Pod
        containerService.start(modelMessage.getUserId(), modelMessage.getId(), streams);


    }

    private List<String> getPodYamlFile(BusinessConfig businessConfig) {

        List<String> podYamlFiles = new ArrayList<>();

        if(businessConfig.isAdversarialAttack()){
            podYamlFiles.add("adversarialAttack");
        }
        if(businessConfig.isBackdoorAttack()){
            podYamlFiles.add("backdoorAttack");
        }
        else{
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "不支持的攻击类型：");
        }

        // 如果没有指定攻击类型，则抛出异常
        if (podYamlFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "没有选择任何攻击类型！");
        }
        return podYamlFiles;
    }

}
