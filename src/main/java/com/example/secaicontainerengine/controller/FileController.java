package com.example.secaicontainerengine.controller;


import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.json.JSONUtil;
import com.example.secaicontainerengine.common.ErrorCode;
import com.example.secaicontainerengine.config.SftpUploader;
import com.example.secaicontainerengine.constant.FileConstant;
import com.example.secaicontainerengine.exception.BusinessException;
import com.example.secaicontainerengine.pojo.dto.file.UploadFileRequest;
import com.example.secaicontainerengine.pojo.dto.model.*;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.pojo.entity.User;
import com.example.secaicontainerengine.pojo.enums.FileUploadBizEnum;
import com.example.secaicontainerengine.service.User.UserService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelEvaluationService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelMessageService;
import com.example.secaicontainerengine.util.FileUtils;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.example.secaicontainerengine.common.ErrorCode.SYSTEM_ERROR;
import static com.example.secaicontainerengine.util.FileUtils.*;
import static com.example.secaicontainerengine.util.FileUtils.generateDetectionEvaluationYaml;

@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    @Autowired
    private ModelEvaluationService modelEvaluationService;

    @Autowired
    private ModelMessageService modelMessageService;

    @Value("${docker.registryHost}")
    private String registryHost;

    @Autowired
    private KubernetesClient K8sClient;

    @Autowired
    private SftpUploader sftpUploader;

    @Value("${nfs.rootPath}")
    private String nfsPath;

    @Value("${nfs.origin-data}")
    private String originDataPath;

    @Value("${nfs.userData}")
    private String userData;

    @Autowired
    private ExecutorService taskExecutor;

    /**
     * 文件上传，并解压文件，同时将解压后的模型的相关文件的地址保存到数据表model_message中
     */
    @PostMapping("/upload/{userId}")
    public Map<String, Object> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                          @RequestPart("data") String rawJsonData,
                                          @PathVariable Long userId
//                                          HttpServletRequest request
                                            ) {

        log.info("========== 接收到的原始 JSON 字符串 ==========");
        log.info(rawJsonData);
        log.info("==========================================");

        // 手动反序列化
        UploadFileRequest uploadFileRequest;
        try {
            uploadFileRequest = JSONUtil.toBean(rawJsonData, UploadFileRequest.class);
        } catch (Exception e) {
            log.error("JSON 反序列化失败", e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "数据格式错误");
        }

        //验证上传的文件是否满足要求
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validFile(multipartFile, fileUploadBizEnum);
        log.info("文件验证通过");

        // 若依调用
        User loginUser = new User();
        loginUser.setId(userId);

        //本服务使用
//        User loginUser = userService.getLoginUser(request);
//        开发时暂时设置不需要登陆
//        User loginUser = new User();
//        loginUser.setId(1242343443L);

        // 文件目录：根据业务、用户来划分
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + multipartFile.getOriginalFilename();
        String filepath = String.format("/%s/%s/%s", fileUploadBizEnum.getValue(), loginUser.getId(), filename);
        String localFilepath = FileConstant.FILE_BASE_PATH + filepath;
        File file = new File(localFilepath);
        // 创建目录
        File parentDirectory = file.getParentFile(); // 获取父目录
        if (!parentDirectory.exists()) {
            boolean created = parentDirectory.mkdirs(); // 创建所有父目录
            if (!created) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法创建文件目录");
            }
        }

        ModelMessage modelMessage = new ModelMessage();
        modelMessage.setUserId(loginUser.getId());

        ModelConfig modelConfig = uploadFileRequest.getModelConfig();
        if(modelConfig != null) {
            String modelNetName = modelConfig.getModelNetName();
            modelMessage.setModelName(modelNetName);
            modelMessage.setModelConfig(JSONUtil.toJsonStr(modelConfig));
        }else{
            throw new BusinessException(SYSTEM_ERROR,"modelConfig上传失败");
        }
        ResourceConfig resourceConfig = uploadFileRequest.getResourceConfig();
        if(resourceConfig != null) {
            modelMessage.setResourceConfig(JSONUtil.toJsonStr(resourceConfig));
        }else{
            throw new BusinessException(SYSTEM_ERROR,"resourceConfig上传失败");
        }
        BusinessConfig businessConfig = uploadFileRequest.getBusinessConfig();
        log.info("========== 后端接收到的数据 ==========");
        log.info("businessConfig 是否为 null: {}", businessConfig == null);
        if(businessConfig != null) {
            log.info("businessConfig.evaluateMethods 数量: {}",
                businessConfig.getEvaluateMethods() != null ? businessConfig.getEvaluateMethods().size() : "null");

            // 打印完整的 businessConfig JSON
            String businessConfigJson = JSONUtil.toJsonStr(businessConfig);
            log.info("完整的 businessConfig JSON:");
            log.info(businessConfigJson);

            // 如果有 evaluateMethods，详细打印每个维度的信息
            if (businessConfig.getEvaluateMethods() != null) {
                for (BusinessConfig.EvaluationDimensionConfig dimConfig : businessConfig.getEvaluateMethods()) {
                    log.info("---------- 维度: {} ----------", dimConfig.getDimension());
                    if (dimConfig.getMethodMetricMap() != null) {
                        for (BusinessConfig.MethodMetricPair pair : dimConfig.getMethodMetricMap()) {
                            log.info("  方法: {}", pair.getMethod());
                            log.info("  metrics: {}", pair.getMetrics());
                            log.info("  attacks: {}", pair.getAttacks());
                            log.info("  fgsmEps: {}", pair.getFgsmEps());
                            log.info("  pgdSteps: {}", pair.getPgdSteps());
                            log.info("  corruptions: {}", pair.getCorruptions());
                        }
                    }
                }
            }
            log.info("========================================");

            modelMessage.setBusinessConfig(businessConfigJson);
        }else{
            throw new BusinessException(SYSTEM_ERROR,"businessConfig上传失败");
        }

        ShowBusinessConfig showBusinessConfig = uploadFileRequest.getShowBusinessConfig();
        if(showBusinessConfig != null){
            modelMessage.setShowBusinessConfig(JSONUtil.toJsonStr(showBusinessConfig));
        }else{
            throw new BusinessException(SYSTEM_ERROR,"businessConfig上传失败");
        }

        modelMessageService.save(modelMessage);
        Long modelId = modelMessage.getId();

        long startTime=System.currentTimeMillis();

        // 使用线程处理文件保存和解压操作
        Future<?> firstTaskFuture = taskExecutor.submit(() -> {
            try {
                // 保存文件到本地
                multipartFile.transferTo(file);
                log.info("文件已保存到: " + localFilepath);

                // 解压文件
                String modelSavePath = unzipFile(localFilepath, parentDirectory.getAbsolutePath());
                log.info("文件已解压到: " + modelSavePath);

                // 获取解压后的目录的最后一级并修改为 modelData
                modelSavePath = FileUtils.renameModelData(modelSavePath);

                // 获取用户上传的文件中的模型代码名称，方便后面评测脚本使用
//                String modelFilePath = modelSavePath + "/" + "model";
//                String modelFileName = findFirstPyFileName(modelFilePath);

                // 复制 Dockerfile 到解压后的目录
//                Path dockerSourcePath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "Docker", "Dockerfile");
                Path dockerSourcePath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "Docker", "Dockerfile_evaluate");

                Path dockerDestinationPath = Paths.get(modelSavePath, "Dockerfile");

                try {
                    Files.copy(dockerSourcePath, dockerDestinationPath);
                    log.info("Dockerfile 已复制到: " + dockerDestinationPath);
                } catch (IOException e) {
                    log.error("Dockerfile 复制失败", e);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "复制 Dockerfile 失败");
                }

                // 模型保存路径（此处可执行进一步操作）
                String condaEnv = processFilesInDirectory(modelMessage, modelSavePath);

                // 将镜像相关脚本文件生成到压缩后的目录
                Path shDestinationPath = Paths.get(modelSavePath, "imageOpe.sh");
                FileUtils.generateImageSh(modelMessage, shDestinationPath, condaEnv, registryHost);

                // 将模型评测相关的代码生成的脚本文件生成到压缩后的目录
                Path runShPath = Paths.get(modelSavePath, "run.sh");
//                FileUtils.generateRunSh(condaEnv, runShPath, modelFileName);
                FileUtils.generateEvaluateRunSh(condaEnv, runShPath);


                // 生成模型评测配置文件(userConfig)
                // 构建创建用户的评测配置文件的对象(userConfig)
                EvaluationConfig evaluationConfig = new EvaluationConfig();
                BeanUtils.copyProperties(modelConfig, evaluationConfig);

                // 解析 inputShape 字符串（格式: "[3,32,32]"），拆分为 channels, height, width
                if (modelConfig.getInputShape() != null && !modelConfig.getInputShape().trim().isEmpty()) {
                    try {
                        String cleanShape = modelConfig.getInputShape().replaceAll("[\\[\\]\\s]", "");
                        String[] parts = cleanShape.split(",");
                        if (parts.length == 3) {
                            evaluationConfig.setInputChannels(Integer.parseInt(parts[0]));
                            evaluationConfig.setInputHeight(Integer.parseInt(parts[1]));
                            evaluationConfig.setInputWidth(Integer.parseInt(parts[2]));
                        }
                    } catch (Exception e) {
                        log.warn("解析 inputShape 失败，使用默认值 [3,32,32]: " + modelConfig.getInputShape(), e);
                        // 使用默认值
                        evaluationConfig.setInputChannels(3);
                        evaluationConfig.setInputHeight(32);
                        evaluationConfig.setInputWidth(32);
                    }
                }

                evaluationConfig.setEvaluateMethods(businessConfig.getEvaluateMethods());
                String configsPath = modelSavePath + "/" + "evaluationConfigs";
                // 根据任务类型选择合适的配置生成方法
                log.info("任务类型: {}", evaluationConfig.getTask());
                if ("classification".equals(evaluationConfig.getTask())) {
                    log.info("使用classification专用模板生成配置文件");
                    FileUtils.generateClassificationEvaluationYaml(evaluationConfig, businessConfig, configsPath);
                } else if ("detection".equals(evaluationConfig.getTask())) {
                    log.info("使用detection专用模板生成配置文件");
                    generateDetectionEvaluationYaml(evaluationConfig, businessConfig, configsPath);
                } else {
                    log.error("不支持的任务类型: {}", evaluationConfig.getTask());
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的任务类型: " + evaluationConfig.getTask());
                }
                log.info("文件处理完成");

            } catch (Exception e) {
                log.error("file upload error, filepath = " + localFilepath, e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
            }
        });


        //把解压后的目录上传到nfs服务器
        taskExecutor.submit(() -> {
            try {
                // 等待第一个任务完成
                firstTaskFuture.get();  // 阻塞，直到第一个任务完成
                log.info("开始上传文件到nfs服务器...");
                // 这里好像有问题，sftp协议好像默认以/为分隔符，如果使用了File.separator在windows系统下则会变成\导致报错
                String remoteDir = nfsPath + "/" + userData + "/" + loginUser.getId() + "/" + modelId;
                remoteDir = remoteDir.replaceAll("\\\\+", "/").replaceAll("/+", "/");
                log.info("远程路径为"+ remoteDir);
                sftpUploader.uploadDirectory(loginUser.getId(), FileConstant.FILE_BASE_PATH + File.separator + fileUploadBizEnum.getValue() + File.separator + loginUser.getId(),
                        remoteDir, modelId);

                long endTime=System.currentTimeMillis();
                //文件上传耗费时间
                long totalTime=endTime-startTime;

                ModelMessage newModelMessage=new ModelMessage();
                newModelMessage.setId(modelId);
                newModelMessage.setUploadcostTime(totalTime/1000);
                newModelMessage.setUploadfinishedTime(LocalDateTime.now());
                modelMessageService.updateById(newModelMessage);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });


        // 返回模型ID,方便点击评测按钮时候确定评测的模型
        Map<String, Object> response = new HashMap<>();
        response.put("modelId", modelId);
        response.put("message", "模型上传中...");
        return response;


    }

    /**
     * 校验文件
     *
     * @param multipartFile
     * @param fileUploadBizEnum 业务类型
     */
    private void validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final long ONE_M = FileConstant.FILE_MAX_SIZE;
        log.info("正在验证文件是否满足要求...");
        if (FileUploadBizEnum.MODEL_DATA.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 20G");
            }
            if (!Arrays.asList("zip","7z").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        }
    }
}
