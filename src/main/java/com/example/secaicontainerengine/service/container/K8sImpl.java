package com.example.secaicontainerengine.service.container;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.constant.EvaluateDimensionConstant;
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


import static com.example.secaicontainerengine.util.YamlUtil.getName;
import static com.example.secaicontainerengine.util.YamlUtil.renderTemplate;

@Service(value = "k8sContainerImpl")
@Slf4j
public class K8sImpl extends ServiceImpl<ContainerMapper, Container> implements ContainerService{

    @Autowired
    private KubernetesClient K8sClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExecutorService taskExecutor;

    @Autowired
    private PodUtil podUtil;

    @Value("${k8s.yaml}")
    private String k8sYaml;

    @Value("${k8s.adversarial-yaml}")
    private String k8sAdversarialYaml;

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

    //初始化接口
    public List<ByteArrayInputStream> init(Long userId, Map<String, String> imageUrl, Map<String, Map> imageParam) throws IOException, TemplateException {
        List<ByteArrayInputStream> streams = new ArrayList<>();
        for(String value: imageUrl.values()){
            //pod命名方式：url+用户id
            String podName = value+userId;
            log.info("初始化接口：Pod的名称-" + podName);
            //准备模板变量
            Map<String, String> values = new HashMap<>();
            values.put("pod_name", podName);
            values.put("container_name", value);
            values.put("image", value);
            //生成填充好的yml文件字节流
            String yamlContent = renderTemplate(k8sYaml, values);
            ByteArrayInputStream ymlStream = new ByteArrayInputStream(yamlContent.getBytes());
            streams.add(ymlStream);
        }
        return streams;
    }

    //初始化接口
    public List<ByteArrayInputStream> initNew(ModelMessage modelMessage, List<String> evaluationTypes) throws IOException, TemplateException {
        List<ByteArrayInputStream> streams = new ArrayList<>();
        for (String evaluationType : evaluationTypes) {
            //pod命名方式：模型id-用户id-评测方法
            String podName = modelMessage.getUserId() + "-" + modelMessage.getId() + "-" + evaluationType.toLowerCase();
            log.info("初始化接口：Pod的名称-" + podName);
            String imageName = registryHost + "/" + modelMessage.getId();

            ResourceConfig podResourceLimits = calculatePodResourceFromModel(modelMessage);
            String gpuCoreLimits = podResourceLimits.getGpuCoreRequired().toString();
            String gpuMemoryLimits = podResourceLimits.getGpuMemoryRequired().toString();
            String gpuNumLimits = podResourceLimits.getGpuNumRequired().toString();

            //准备模板变量
            Map<String, String> values = new HashMap<>();
            values.put("podName", podName);
            values.put("containerName", podName);
            values.put("imageName", imageName);
            values.put("userData", userData);
            values.put("evaluationData", evaluationData);
            values.put("evaluationType", evaluationType);
            values.put("systemData", systemData);
            values.put("nfsIP",nfsIp);
            values.put("rootPath",rootPath);
            values.put("userId",String.valueOf(modelMessage.getUserId()));
            values.put("modelId",String.valueOf(modelMessage.getId()));
            values.put("gpuCoreLimits",gpuCoreLimits);
            values.put("gpuMemoryLimits",gpuMemoryLimits);
            values.put("gpuNumLimits",gpuNumLimits);
            values.put("evaluateDimension", evaluationType);
            values.put("logUrl", logUrl);
            values.put("resultUrl", resultUrl);
            values.put("resultColumn", evaluationType+"Result");


            //生成填充好的yml文件字节流
            String yamlContent = renderTemplate(k8sAdversarialGpuYaml, values);
//            String yamlContent = renderTemplate(k8sAdversarialYaml, values);
            ByteArrayInputStream ymlStream = new ByteArrayInputStream(yamlContent.getBytes());
            streams.add(ymlStream);


        }
        return streams;
    }

    //启动接口
    public void start(Long userId, Long modelId, List<ByteArrayInputStream> streams) throws IOException {

        for (ByteArrayInputStream stream : streams) {
            // 2.获取容器的名字
            String containerName = getName(stream);

            // 3.获取到pod对应的评测任务对应的评测方法
            String evaluateMethod = containerName.split("-")[2];
            QueryWrapper<EvaluationMethod> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("methodName", evaluateMethod);
            EvaluationMethod evaluationMethod = evaluationMethodMapper.selectOne(queryWrapper);
            Long evaluationMethodId = evaluationMethod.getId();

            // 4.启动评测任务
            taskExecutor.execute(() -> {
                // 记录开始时间
                long startTime = System.currentTimeMillis();

                // 修改单次评测任务表评测状态
                EvaluationResult evaluationResult = EvaluationResult.builder()
                        .evaluateMethodId(evaluationMethodId)
                        .userId(userId)
                        .modelId(modelId)
                        .status("评测中")
                        .build();
                evaluationResultMapper.insert(evaluationResult);

                // 创建容器
                HasMetadata metadata = K8sClient.resource(stream).inNamespace("default").create();

                // 记录结束时间
                long endTime = System.currentTimeMillis();

                // 评测任务开启到创建pod命令发出的时间
                long executionTime = endTime - startTime;

                System.out.println("开启pod花费时间: " + executionTime + " 毫秒");
                watchStatus(userId, modelId, containerName);
            });
        }
    }

    //监听接口1-持续监听指定的容器状态
    public void watchStatus(Long userId, Long modelId, String containerName) {
        final CountDownLatch closeLatch = new CountDownLatch(1);

        // 新增：使用Map记录Pod关键状态的时间戳（避免重复记录）
        Map<String, Instant> statusTimestamps = new ConcurrentHashMap<>();

        Watch watch = K8sClient.pods().inNamespace("default").withName(containerName).watch(new Watcher<Pod>() {
            @Override
            public void eventReceived(Action action, Pod pod) {
                String phase = pod.getStatus().getPhase();
                log.info("action: " + action +" phase：" + phase);

                // ==== 1. 记录Pod创建时间（全局起始时间） ====
                Instant creationTime = Instant.parse(pod.getMetadata().getCreationTimestamp());
                statusTimestamps.putIfAbsent("creationTime", creationTime);

                // ==== 2. 记录ContainerCreating开始时间（镜像拉取阶段） ====
                if (phase.equals("Pending")) {
                    // 获取容器状态列表（每个容器的详细状态）
                    List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
                    if (containerStatuses != null && !containerStatuses.isEmpty()) {
                        for (ContainerStatus cs : containerStatuses) {
                            // 检查容器是否处于等待状态（如创建中）
                            if (cs.getState() != null && cs.getState().getWaiting() != null) {
                                String waitingReason = cs.getState().getWaiting().getReason();
                                if ("ContainerCreating".equals(waitingReason)) { // 直接匹配容器创建状态
                                    statusTimestamps.putIfAbsent("containerCreatingStart", Instant.now());
                                    log.info("容器 {} 进入创建状态（ContainerCreating），时间戳记录成功", cs.getName());
                                    break; // 找到后退出，避免重复处理
                                }
                            }
                        }
                    }
                }

                // ==== 3. 记录Running时间（镜像拉取结束，执行开始） ====
                if (phase.equals("Running") && !statusTimestamps.containsKey("runningTime")) {
                    Instant runningTime = Instant.now();
                    log.info("Pod {} 进入运行状态", containerName);
                    statusTimestamps.put("runningStart", runningTime);
                }

                // ==== 4. 记录Succeeded时间（执行结束） ====
                if (phase.equals("Succeeded") && !statusTimestamps.containsKey("succeededTime")) {
                    // 仅执行一次（避免重复触发）
                    if (!statusTimestamps.containsKey("succeededStart")) {
                        statusTimestamps.put("succeededStart", Instant.now());
                        log.info("Pod {} 运行完成（状态：{}），开始获取监控日志...", containerName, phase);

                        try {
                            // 获取监控容器的日志
                            String monitorLogs = K8sClient.pods()
                                    .inNamespace("default")
                                    .withName(containerName)
                                    .inContainer(containerName)
                                    .getLog();

                            if (monitorLogs != null && !monitorLogs.isEmpty()) {
                                log.info("监控容器日志:\n{}", monitorLogs);
                                // 解析日志并存储监控结果（调用你的解析方法）
                                parseMonitorResults(monitorLogs, containerName);
                            } else {
                                log.warn("未获取到监控容器日志");
                            }
                        } catch (Exception e) {
                            log.error("获取监控日志失败，Pod名称：{}", containerName, e);
                        }
                        // 记录时间监控信息（原有逻辑）
                        try {
                            recordPodTime(containerName, statusTimestamps);
                        } catch (JsonProcessingException e) {
                            log.error("记录Pod时间信息失败", e);
                        }
                    }
                }


                Container container = Container.builder()
                        .containerName(containerName)
                        .nameSpace(pod.getMetadata().getNamespace())
                        .status(pod.getStatus().getPhase())
                        .restarts(0)
                        .AGE(String.valueOf(Duration.between(
                                OffsetDateTime.parse(pod.getMetadata().getCreationTimestamp()).toInstant(),
                                Instant.now()).getSeconds()))
                        .nodeName(pod.getStatus().getNominatedNodeName())
                        .imageId(0L)
                        .modelId(modelId)
                        .updateTime(LocalDateTime.now())
                        .build();

                switch (action) {
                    case ADDED:
                    case MODIFIED: {
                        if(phase.equals("Running")) {
                            log.info("启动接口：已启动的Pod名称-" + containerName);
                            //2.保存容器实例到mysql中
                            Container existContainer = containerMapper.selectOne(new LambdaQueryWrapper<Container>()
                                    .eq(Container::getContainerName, containerName));
                            if(existContainer != null) {
                                //如果当前容器实例已存在，则更新
                                containerMapper.update(container, new LambdaQueryWrapper<Container>()
                                        .eq(Container::getContainerName, containerName));
                            }else {
                                //如果当前容器实例不存在，则插入一条新的容器实例
                                containerMapper.insert(container);
                            }
//                        } else if (phase.equals("Succeeded") || phase.equals("Failed")) {
                        } else if (phase.equals("Succeeded")) {
//                            latch.countDown();
//                            deleteSingle(userId, containerName);
                            Container existContainer = containerMapper.selectOne(new LambdaQueryWrapper<Container>()
                                    .eq(Container::getContainerName, containerName));
                            if(existContainer != null) {
                                //如果当前容器实例已存在，则更新
                                containerMapper.update(container, new LambdaQueryWrapper<Container>()
                                        .eq(Container::getContainerName, containerName));
                            }else {
                                //如果当前容器实例不存在，则插入一条新的容器实例
                                containerMapper.insert(container);
                            }
                        }
                        break;
                    }
                    case DELETED: {
                        closeLatch.countDown();
                        break;
                    }
                }
            }

            @Override
            public void onClose(WatcherException cause) {

            }
        });
        try {
            closeLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            watch.close();
        }
    }

    //监听接口2-只输出一次容器的状态
    public String getStatus(String containerName) {
        String name = containerMapper.getStatusByContainerName(containerName);
        if (name == null)
            return containerName+"容器不存在";
        return name;
    }

    //回收接口2-删除用户的单个容器
    public void deleteSingle(Long userId, String containerName) {
        K8sClient.pods().inNamespace("default").withName(containerName).delete();
        log.info("回收接口：已删除Pod-" + containerName);
    }


    public List<String> getContainersByModelId(Long modelId) {
        return containerMapper.getContainerNameByModelId(modelId);
    }

    // 解析监控时间并存入到数据库当中
    public void recordPodTime(String containerName, Map<String, Instant> statusTimestamps) throws JsonProcessingException {
        Instant containerCreatingStart = statusTimestamps.get("containerCreatingStart");
        Instant runningStart = statusTimestamps.get("runningStart");
        Instant succeededStart = statusTimestamps.get("succeededStart");
        Long createImageTime = 0L;
        Long containerCreatingTime = 0L;
        Long runningTime = 0L;
        if (containerCreatingStart != null && runningStart != null) {
            containerCreatingTime = Duration.between(containerCreatingStart, runningStart).toMillis();
            log.info("Pod {} 拉取镜像耗时：{}ms", containerName, containerCreatingTime);
        }
        if(succeededStart != null){
            runningTime = Duration.between(runningStart, succeededStart).toMillis();
            log.info("Pod {} 执行耗时：{}ms", containerName, runningTime);
        }
        Long modelId = Long.parseLong(containerName.split("-")[1]);

        String evaluateMethod = containerName.split("-")[2];
        QueryWrapper<EvaluationMethod> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("methodName", evaluateMethod);
        EvaluationMethod evaluationMethod = evaluationMethodMapper.selectOne(queryWrapper);
        Long evaluationMethodId = evaluationMethod.getId();

        if(modelId != null){
            ModelEvaluation modelEvaluation = modelEvaluationMapper.selectOne(new LambdaQueryWrapper<ModelEvaluation>()
                    .eq(ModelEvaluation::getModelId, modelId));
            if(modelEvaluation != null){
                createImageTime = modelEvaluation.getCreateImageTime();
            }
            Long totalTime = createImageTime + containerCreatingTime + runningTime;

            EvaluationResultTimeUse evaluationResultTimeUse = EvaluationResultTimeUse.builder()
                    .totalTime(totalTime)
                    .createImageTime(createImageTime)
                    .containerCreatingTime(containerCreatingTime)
                    .runningTime(runningTime)
                    .build();
            ObjectMapper objectMapper = new ObjectMapper();
            String timeUse = objectMapper.writeValueAsString(evaluationResultTimeUse);

            LambdaQueryWrapper<EvaluationResult> queryWrapper2 = new LambdaQueryWrapper<>();
            queryWrapper2.eq(EvaluationResult::getModelId, modelId)
                    .eq(EvaluationResult::getEvaluateMethodId, evaluationMethodId);

            EvaluationResult evaluationResult = evaluationResultMapper.selectOne(queryWrapper2);

            evaluationResult.setTimeUse(timeUse);
            evaluationResultMapper.updateById(evaluationResult);
        }
    }

    // 解析监控日志内容并更新到数据库当中
    public void parseMonitorResults(String monitorLogs, String podName){

        Long modelId = Long.parseLong(podName.split("-")[1]);
        String evaluateMethod = podName.split("-")[2];
        QueryWrapper<EvaluationMethod> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("methodName", evaluateMethod);
        EvaluationMethod evaluationMethod = evaluationMethodMapper.selectOne(queryWrapper);
        Long evaluationMethodId = evaluationMethod.getId();

        LambdaQueryWrapper<EvaluationResult> queryWrapper2 = new LambdaQueryWrapper<>();
        queryWrapper2.eq(EvaluationResult::getModelId, modelId)
                .eq(EvaluationResult::getEvaluateMethodId, evaluationMethodId);
        EvaluationResult evaluationResult = evaluationResultMapper.selectOne(queryWrapper2);

        for (String line : monitorLogs.split("\n")) {
            if (line.contains("评测任务最大内存占用")) {
                // 匹配数字部分（支持整数和小数，如1021.00或201）
                String mem = line.split(":")[1].split(" ")[0];
                // 转换为Long（注意：小数会丢失精度，如需保留小数可改用Double）
                evaluationResult.setCpuMemoryUse(Long.parseLong(mem));
                log.info("解析到最大内存占用: {}", mem);
            } else if (line.contains("评测任务最大显存占用")) {
                String gpuMem = line.split(":")[1].split(" ")[0];
                evaluationResult.setGpuMemoryUse(Long.parseLong(gpuMem));
                log.info("解析到最大显存占用: {}", gpuMem);
            }
        }
        evaluationResultMapper.updateById(evaluationResult);
    }

    @Override
    // 从用户填入的模型运行需要的资源信息计算模型评测开启的pod的资源信息
    public ResourceConfig calculatePodResourceFromModel(ModelMessage modelMessage){

        // 获取到具体的资源信息
        String resourceConfigStr = modelMessage.getResourceConfig();
        ResourceConfig resourceConfig = JSONUtil.toBean(resourceConfigStr, ResourceConfig.class);
        Integer gpuNumRequired = resourceConfig.getGpuNumRequired();
        Integer gpuMemoryRequired = resourceConfig.getGpuMemoryRequired();
        Integer gpuCoreRequired = resourceConfig.getGpuCoreRequired();

        // 计算pod相关yaml文件中limits资源
        Integer gpuMemoryLimits = gpuMemoryRequired + evaluationGpuMemory;
        Integer gpuCoreLimits = gpuCoreRequired + evaluationGpuCore;
        Integer gpuNumLimits = gpuNumRequired + evaluationGpuNum;

        // 返回
        ResourceConfig podResourceLimits = ResourceConfig.builder()
                .gpuMemoryRequired(gpuMemoryLimits)
                .gpuCoreRequired(gpuCoreLimits)
                .gpuNumRequired(gpuNumLimits)
                .build();

        return podResourceLimits;
    }


}
