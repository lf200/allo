package com.example.secaicontainerengine.util;

import cn.hutool.json.JSONUtil;
import com.example.secaicontainerengine.pojo.dto.model.ResourceConfig;
import com.example.secaicontainerengine.pojo.dto.result.PodResult;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
public class PodUtil {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private KubernetesClient K8sClient;

    //判断pod是否已经存在于集群
    public boolean isExistCluster(String podName) {
        if(podName == null) {
            return false;
        }
        PodList podList = K8sClient.pods().inNamespace("default").list();
        for (Pod pod : podList.getItems()) {
            if (pod.getMetadata().getName().equals(podName)) {
                return true;
            }
        }
        return false;
    }

    // 从评测结果计算得分
    // todo 示例逻辑，需根据实际业务调整
    public static BigDecimal calculateScoreFromResult(PodResult rawResult){
        BigDecimal accAdv = rawResult.getAccAdv();
        BigDecimal accClean = rawResult.getAccClean();
        BigDecimal result = null;
        if (accClean.signum() == 0) {
            throw new IllegalArgumentException("除数 accClean 不能为 0");
        } else {
            // 设定小数位数和舍入模式（示例：保留4位小数，四舍五入）
            int scale = 4;
            RoundingMode roundingMode = RoundingMode.HALF_UP;
            result = accAdv.divide(accClean, scale, roundingMode);
        }
        return result;
    }

    /**
     * 将指定地址的文件上传到指定的 Pod 中
     *
     * @param client      KubernetesClient 实例
     * @param namespace   Pod 所在的命名空间
     * @param podName     Pod 的名称
     * @param filePath    本地文件路径
     * @param targetPath  Pod 内目标文件路径
     * @return 是否上传成功
     */
    public static boolean uploadFileToPod(KubernetesClient client, String namespace, String podName, String filePath, String targetPath) {
        File fileToUpload = new File(filePath); // 要上传的文件
        try {
            // 检查文件是否存在
            if (!fileToUpload.exists() || !fileToUpload.isFile()) {
                log.error("文件不存在或不是文件: {}", filePath);
                throw new IllegalArgumentException("文件路径无效: " + filePath);
            }
            // 上传文件到 Pod
            client.pods()
                .inNamespace(namespace)    // 指定命名空间
                .withName(podName)         // 指定 Pod 名称
                .file(targetPath)          // Pod 内的目标路径 /home/..
                .upload(fileToUpload.toPath()); // 上传文件
            log.info("文件上传成功: {} -> {} (Pod: {}, Namespace: {})", filePath, targetPath, podName, namespace);
            return true;
        } catch (Exception e) {
            log.error("文件上传失败: {} -> {} (Pod: {}, Namespace: {})", filePath, targetPath, podName, namespace, e);
            return false;
        }
    }
}
