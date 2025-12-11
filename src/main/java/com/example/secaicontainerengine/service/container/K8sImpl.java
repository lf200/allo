package com.example.secaicontainerengine.service.container;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.mapper.ContainerMapper;
import com.example.secaicontainerengine.mapper.EvaluationMethodMapper;
import com.example.secaicontainerengine.mapper.EvaluationResultMapper;
import com.example.secaicontainerengine.mapper.ModelEvaluationMapper;
import com.example.secaicontainerengine.pojo.dto.model.EvaluationResultTimeUse;
import com.example.secaicontainerengine.pojo.dto.model.ResourceConfig;
import com.example.secaicontainerengine.pojo.entity.*;
import com.example.secaicontainerengine.service.modelEvaluation.EvaluationResultService;
import com.example.secaicontainerengine.util.PodUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.TemplateException;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.example.secaicontainerengine.util.YamlUtil.getName;
import static com.example.secaicontainerengine.util.YamlUtil.renderTemplate;

@Service(value = "k8sContainerImpl")
@Slf4j
public class K8sImpl extends ServiceImpl<ContainerMapper, Container> implements ContainerService {

    @Autowired
    private KubernetesClient K8sClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutorService taskExecutor;

    @Autowired
    private PodUtil podUtil;

    @Value("${k8s.adversarial-gpu-yaml}")
    private String k8sAdversarialGpuYaml;

    @Value("${nfs.rootPath}")
    private String rootPath;

    @Value("${sftp.host}")
    private String nfsIp;

    @Autowired
    private ContainerMapper containerMapper;

    @Value("${nfs.userData}")
    private String userData;

    @Value("${nfs.systemData}")
    private String systemData;

    @Value("${nfs.evaluationData}")
    private String evaluationData;

    @Value("${docker.registryHost}")
    private String registryHost;

    @Autowired
    private EvaluationResultService evaluationResultService;

    @Autowired
    private EvaluationMethodMapper evaluationMethodMapper;

    @Autowired
    private EvaluationResultMapper evaluationResultMapper;

    @Lazy
    @Autowired
    private ModelEvaluationMapper modelEvaluationMapper;

    @Value("${k8s.evaluation-resources.limits.gpu-memory}")
    private Integer evaluationGpuMemory;

    @Value("${k8s.evaluation-resources.limits.gpu-core}")
    private Integer evaluationGpuCore;

    @Value("${k8s.evaluation-resources.limits.gpu-num}")
    private Integer evaluationGpuNum;

    @Value("${localhost.logUrl}")
    private String logUrl;

    @Value("${localhost.resultUrl}")
    private String resultUrl;

    // 初始化接口
    @Override
    public List<ByteArrayInputStream> initNew(ModelMessage modelMessage, List<String> evaluationTypes) throws IOException, TemplateException {
        List<ByteArrayInputStream> streams = new ArrayList<>();
        for (String evaluationType : evaluationTypes) {
            String podName = modelMessage.getUserId() + "-" + modelMessage.getId() + "-" + evaluationType.toLowerCase();
            log.info("初始化接口：Pod的名称-{}", podName);

            String imageName = registryHost + "/" + modelMessage.getId();
            ResourceConfig podResourceLimits = calculatePodResourceFromModel(modelMessage);

            Map<String, String> values = new HashMap<>();
            values.put("podName", podName);
            values.put("containerName", podName);
            values.put("imageName", imageName);
            values.put("userData", userData);
            values.put("evaluationData", evaluationData);
            values.put("evaluationType", evaluationType);
            values.put("systemData", systemData);
            values.put("nfsIP", nfsIp);
            values.put("rootPath", rootPath);
            values.put("userId", String.valueOf(modelMessage.getUserId()));
            values.put("modelId", String.valueOf(modelMessage.getId()));
            values.put("gpuCoreLimits", podResourceLimits.getGpuCoreRequired().toString());
            values.put("gpuMemoryLimits", podResourceLimits.getGpuMemoryRequired().toString());
            values.put("gpuNumLimits", podResourceLimits.getGpuNumRequired().toString());
            values.put("evaluateDimension", evaluationType);
            values.put("logUrl", logUrl);
            values.put("resultUrl", resultUrl);
            values.put("resultColumn", evaluationType + "Result");

            String yamlContent = renderTemplate(k8sAdversarialGpuYaml, values);
            streams.add(new ByteArrayInputStream(yamlContent.getBytes()));

            log.info("初始化接口：streams构建完毕");
        }
        return streams;
    }

    // 启动接口
    @Override
    public void start(Long userId, Long modelId, List<ByteArrayInputStream> streams) throws IOException {

        for (ByteArrayInputStream stream : streams) {

            // 重要：getName 会读取 stream，所以要复制一份用于真正 create
            byte[] yamlBytes = stream.readAllBytes();
            ByteArrayInputStream streamForName = new ByteArrayInputStream(yamlBytes);
            ByteArrayInputStream streamForCreate = new ByteArrayInputStream(yamlBytes);

            String containerName = getName(streamForName);
            String evaluateMethod = containerName.split("-")[2];

            EvaluationMethod evaluationMethod = evaluationMethodMapper.selectOne(
                    new QueryWrapper<EvaluationMethod>().eq("methodName", evaluateMethod)
            );
            Long evaluationMethodId = evaluationMethod.getId();

            taskExecutor.execute(() -> {
                long startTime = System.currentTimeMillis();

                // === (1) Upsert evaluation_result，避免重复插入 ===
                LambdaQueryWrapper<EvaluationResult> erQw = new LambdaQueryWrapper<>();
                erQw.eq(EvaluationResult::getModelId, modelId)
                        .eq(EvaluationResult::getEvaluateMethodId, evaluationMethodId);

                EvaluationResult exist = evaluationResultMapper.selectOne(erQw);
                if (exist == null) {
                    exist = EvaluationResult.builder()
                            .evaluateMethodId(evaluationMethodId)
                            .userId(userId)
                            .modelId(modelId)
                            .status("评测中")
                            .build();
                    evaluationResultMapper.insert(exist);
                } else {
                    exist.setStatus("评测中");
                    exist.setUpdateTime(LocalDateTime.now());
                    evaluationResultMapper.updateById(exist);
                }

                try {
                    // === (2) Pod 已存在就先删 ===
                    Pod oldPod = K8sClient.pods().inNamespace("default").withName(containerName).get();
                    if (oldPod != null) {
                        log.warn("Pod {} 已存在，先删除再重建", containerName);
                        K8sClient.pods().inNamespace("default").withName(containerName).delete();
                        // 简单等一小会让 apiserver 清理（避免立刻 409）
                        Thread.sleep(1500);
                    }

                    // === (3) 正式创建 Pod ===
                    HasMetadata metadata = K8sClient.resource(streamForCreate).inNamespace("default").create();
                    log.info("Pod {} 创建成功: {}", containerName, metadata.getMetadata().getName());

                } catch (Exception e) {
                    log.error("Pod {} 创建失败", containerName, e);
                    exist.setStatus("失败");
                    exist.setUpdateTime(LocalDateTime.now());
                    evaluationResultMapper.updateById(exist);
                    return;
                }

                long endTime = System.currentTimeMillis();
                log.info("开启pod花费时间: {} 毫秒", (endTime - startTime));

                watchStatus(userId, modelId, containerName);
            });
        }
    }

    @Override
    public void watchStatus(Long userId, Long modelId, String containerName) {
        final CountDownLatch closeLatch = new CountDownLatch(1);
        Map<String, Instant> statusTimestamps = new ConcurrentHashMap<>();

        Watch watch = K8sClient.pods()
                .inNamespace("default")
                .withName(containerName)
                .watch(new Watcher<Pod>() {

                    @Override
                    public void eventReceived(Action action, Pod pod) {
                        String phase = pod.getStatus().getPhase();
                        log.info("action: {} phase: {}", action, phase);

                // 打印详细的 Pending 原因（用于调试）
                if (phase.equals("Pending")) {
                    // 打印 Pod Conditions（状态条件）
                    if (pod.getStatus().getConditions() != null) {
                        pod.getStatus().getConditions().forEach(condition -> {
                            log.info("Pod Condition - Type: {}, Status: {}, Reason: {}, Message: {}",
                                condition.getType(), condition.getStatus(),
                                condition.getReason(), condition.getMessage());
                        });
                    }
                    // 打印容器状态（等待原因）
                    if (pod.getStatus().getContainerStatuses() != null) {
                        pod.getStatus().getContainerStatuses().forEach(cs -> {
                            if (cs.getState() != null && cs.getState().getWaiting() != null) {
                                log.warn("容器 {} 等待中 - Reason: {}, Message: {}",
                                    cs.getName(),
                                    cs.getState().getWaiting().getReason(),
                                    cs.getState().getWaiting().getMessage());
                            }
                        });
                    }
                }

                        // 记录 creationTime
                        statusTimestamps.putIfAbsent("creationTime",
                                Instant.parse(pod.getMetadata().getCreationTimestamp()));

                        // Pending -> ContainerCreating
                        if ("Pending".equals(phase)) {
                            List<ContainerStatus> css = pod.getStatus().getContainerStatuses();
                            if (css != null) {
                                for (ContainerStatus cs : css) {
                                    if (cs.getState() != null && cs.getState().getWaiting() != null) {
                                        if ("ContainerCreating".equals(cs.getState().getWaiting().getReason())) {
                                            statusTimestamps.putIfAbsent("containerCreatingStart", Instant.now());
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        // Running
                        if ("Running".equals(phase)) {
                            statusTimestamps.putIfAbsent("runningStart", Instant.now());
                        }

                        // === 关键：Succeeded / Failed 都处理 ===
                        if (("Succeeded".equals(phase) || "Failed".equals(phase))
                                && !statusTimestamps.containsKey("finishStart")) {

                            statusTimestamps.put("finishStart", Instant.now());
                            log.info("Pod {} 结束，phase={}", containerName, phase);

                            try {
                                String logs = K8sClient.pods()
                                        .inNamespace("default")
                                        .withName(containerName)
                                        .inContainer(containerName)
                                        .getLog();
                                if (logs != null && !logs.isEmpty()) {
                                    parseMonitorResults(logs, containerName);
                                }
                            } catch (Exception e) {
                                log.error("获取监控日志失败: {}", containerName, e);
                            }

                            try {
                                recordPodTime(containerName, statusTimestamps);
                            } catch (Exception e) {
                                log.error("记录Pod时间失败", e);
                            }

                            // === 更新 evaluation_result 最终状态 ===
                            updateEvaluationResultFinalStatus(containerName, phase);

                            // === Pod 完成后删除，释放资源，同时触发 DELETED 让 watch 关闭 ===
                            deleteSingle(userId, containerName);
                        }

                        // container 表更新（Running / Succeeded / Failed 都更新）
                        if (action == Action.ADDED || action == Action.MODIFIED) {
                            Container c = Container.builder()
                                    .containerName(containerName)
                                    .nameSpace(pod.getMetadata().getNamespace())
                                    .status(phase)
                                    .restarts(0)
                                    .AGE(String.valueOf(Duration.between(
                                            OffsetDateTime.parse(pod.getMetadata().getCreationTimestamp()).toInstant(),
                                            Instant.now()).getSeconds()))
                                    .nodeName(pod.getStatus().getNominatedNodeName())
                                    .imageId(0L)
                                    .modelId(modelId)
                                    .updateTime(LocalDateTime.now())
                                    .build();

                            Container existC = containerMapper.selectOne(
                                    new LambdaQueryWrapper<Container>()
                                            .eq(Container::getContainerName, containerName)
                            );
                            if (existC != null) {
                                containerMapper.update(c,
                                        new LambdaQueryWrapper<Container>()
                                                .eq(Container::getContainerName, containerName));
                            } else {
                                containerMapper.insert(c);
                            }
                        }

                        if (action == Action.DELETED) {
                            closeLatch.countDown();
                        }
                    }

                    @Override
                    public void onClose(WatcherException cause) {
                        closeLatch.countDown();
                    }
                });

        try {
            closeLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            watch.close();
        }
    }

    private void updateEvaluationResultFinalStatus(String podName, String phase) {
        Long modelId = Long.parseLong(podName.split("-")[1]);
        String evaluateMethod = podName.split("-")[2];

        EvaluationMethod method = evaluationMethodMapper.selectOne(
                new QueryWrapper<EvaluationMethod>().eq("methodName", evaluateMethod)
        );

        LambdaQueryWrapper<EvaluationResult> qw = new LambdaQueryWrapper<>();
        qw.eq(EvaluationResult::getModelId, modelId)
                .eq(EvaluationResult::getEvaluateMethodId, method.getId());

        EvaluationResult er = evaluationResultMapper.selectOne(qw);
        if (er != null) {
            er.setStatus("Succeeded".equals(phase) ? "成功" : "失败");
            er.setUpdateTime(LocalDateTime.now());
            evaluationResultMapper.updateById(er);
        }
    }

    @Override
    public void deleteSingle(Long userId, String containerName) {
        K8sClient.pods().inNamespace("default").withName(containerName).delete();
        log.info("回收接口：已删除Pod-{}", containerName);
    }

    @Override
    public String getStatus(String containerName) {
        String name = containerMapper.getStatusByContainerName(containerName);
        return name == null ? containerName + "容器不存在" : name;
    }

    @Override
    public List<String> getContainersByModelId(Long modelId) {
        return containerMapper.getContainerNameByModelId(modelId);
    }

    // ===== 原有 recordPodTime / parseMonitorResults / calculatePodResourceFromModel 不变 =====

    public void recordPodTime(String containerName, Map<String, Instant> statusTimestamps) throws JsonProcessingException {
        Instant containerCreatingStart = statusTimestamps.get("containerCreatingStart");
        Instant runningStart = statusTimestamps.get("runningStart");
        Instant finishStart = statusTimestamps.get("finishStart");

        Long containerCreatingTime = 0L;
        Long runningTime = 0L;

        if (containerCreatingStart != null && runningStart != null) {
            containerCreatingTime = Duration.between(containerCreatingStart, runningStart).toMillis();
        }
        if (runningStart != null && finishStart != null) {
            runningTime = Duration.between(runningStart, finishStart).toMillis();
        }

        Long modelId = Long.parseLong(containerName.split("-")[1]);
        String evaluateMethod = containerName.split("-")[2];

        EvaluationMethod evaluationMethod = evaluationMethodMapper.selectOne(
                new QueryWrapper<EvaluationMethod>().eq("methodName", evaluateMethod)
        );
        Long evaluationMethodId = evaluationMethod.getId();

        Long createImageTime = 0L;
        ModelEvaluation modelEvaluation = modelEvaluationMapper.selectOne(
                new LambdaQueryWrapper<ModelEvaluation>().eq(ModelEvaluation::getModelId, modelId)
        );
        if (modelEvaluation != null) {
            createImageTime = modelEvaluation.getCreateImageTime();
        }

        Long totalTime = createImageTime + containerCreatingTime + runningTime;

        EvaluationResultTimeUse timeUseObj = EvaluationResultTimeUse.builder()
                .totalTime(totalTime)
                .createImageTime(createImageTime)
                .containerCreatingTime(containerCreatingTime)
                .runningTime(runningTime)
                .build();

        String timeUse = new ObjectMapper().writeValueAsString(timeUseObj);

        LambdaQueryWrapper<EvaluationResult> qw = new LambdaQueryWrapper<>();
        qw.eq(EvaluationResult::getModelId, modelId)
                .eq(EvaluationResult::getEvaluateMethodId, evaluationMethodId);

        EvaluationResult er = evaluationResultMapper.selectOne(qw);
        if (er != null) {
            er.setTimeUse(timeUse);
            evaluationResultMapper.updateById(er);
        }
    }

    public void parseMonitorResults(String monitorLogs, String podName) {
        Long modelId = Long.parseLong(podName.split("-")[1]);
        String evaluateMethod = podName.split("-")[2];

        EvaluationMethod method = evaluationMethodMapper.selectOne(
                new QueryWrapper<EvaluationMethod>().eq("methodName", evaluateMethod)
        );

        LambdaQueryWrapper<EvaluationResult> qw = new LambdaQueryWrapper<>();
        qw.eq(EvaluationResult::getModelId, modelId)
                .eq(EvaluationResult::getEvaluateMethodId, method.getId());

        EvaluationResult er = evaluationResultMapper.selectOne(qw);
        if (er == null) return;

        for (String line : monitorLogs.split("\n")) {
            if (line.contains("评测任务最大内存占用")) {
                String mem = line.split(":")[1].split(" ")[0];
                er.setCpuMemoryUse(Long.parseLong(mem));
            } else if (line.contains("评测任务最大显存占用")) {
                String gpuMem = line.split(":")[1].split(" ")[0];
                er.setGpuMemoryUse(Long.parseLong(gpuMem));
            }
        }
        evaluationResultMapper.updateById(er);
    }

    @Override
    public ResourceConfig calculatePodResourceFromModel(ModelMessage modelMessage) {
        String resourceConfigStr = modelMessage.getResourceConfig();
        ResourceConfig resourceConfig = JSONUtil.toBean(resourceConfigStr, ResourceConfig.class);

        Integer gpuMemoryLimits = resourceConfig.getGpuMemoryRequired() + evaluationGpuMemory;
        Integer gpuCoreLimits = resourceConfig.getGpuCoreRequired() + evaluationGpuCore;
        Integer gpuNumLimits = resourceConfig.getGpuNumRequired() + evaluationGpuNum;

        return ResourceConfig.builder()
                .gpuMemoryRequired(gpuMemoryLimits)
                .gpuCoreRequired(gpuCoreLimits)
                .gpuNumRequired(gpuNumLimits)
                .build();
    }

    // 旧 init() 不用可留空
    @Override
    public List<ByteArrayInputStream> init(Long userId, Map<String, String> imageUrl, Map<String, Map> imageParam) {
        return List.of();
    }

}
