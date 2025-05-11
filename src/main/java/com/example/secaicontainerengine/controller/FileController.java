package com.example.secaicontainerengine.controller;


import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.example.secaicontainerengine.common.ErrorCode;
import com.example.secaicontainerengine.config.SftpUploader;
import com.example.secaicontainerengine.constant.FileConstant;
import com.example.secaicontainerengine.exception.BusinessException;
import com.example.secaicontainerengine.pojo.dto.file.UploadFileRequest;
import com.example.secaicontainerengine.pojo.dto.model.BusinessConfig;
import com.example.secaicontainerengine.pojo.dto.model.EvaluationConfig;
import com.example.secaicontainerengine.pojo.dto.model.ModelConfig;
import com.example.secaicontainerengine.pojo.dto.model.ResourceConfig;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.pojo.entity.User;
import com.example.secaicontainerengine.pojo.enums.FileUploadBizEnum;
import com.example.secaicontainerengine.service.User.UserService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelEvaluationService;
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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.example.secaicontainerengine.common.ErrorCode.SYSTEM_ERROR;
import static com.example.secaicontainerengine.util.FileUtils.*;

@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    @Autowired
    private ModelEvaluationService modelEvaluationService;

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
    @PostMapping("/upload")
    public Map<String, Object> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                          UploadFileRequest uploadFileRequest,
                                          HttpServletRequest request) {

        //验证上传的文件是否满足要求
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validFile(multipartFile, fileUploadBizEnum);
        log.info("文件验证通过");

//        User loginUser = userService.getLoginUser(request);
        //开发时暂时设置不需要登陆
        User loginUser = new User();
        loginUser.setId(1242343443L);

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
        modelEvaluationService.save(modelMessage);
        Long modelId = modelMessage.getId();


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
                String modelFilePath = modelSavePath + "/" + "model";
                String modelFileName = findFirstPyFileName(modelFilePath);

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
                String remoteDir = nfsPath + File.separator + userData +
                        File.separator + loginUser.getId() +
                        File.separator + modelId;
                sftpUploader.uploadDirectory(loginUser.getId(), FileConstant.FILE_BASE_PATH + File.separator + fileUploadBizEnum.getValue() + File.separator + loginUser.getId(),
                        remoteDir, modelId);

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
