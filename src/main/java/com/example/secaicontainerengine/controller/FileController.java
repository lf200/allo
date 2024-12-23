package com.example.secaicontainerengine.controller;


import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.example.secaicontainerengine.common.BaseResponse;
import com.example.secaicontainerengine.common.ErrorCode;
import com.example.secaicontainerengine.common.ResultUtils;
import com.example.secaicontainerengine.constant.FileConstant;
import com.example.secaicontainerengine.exception.BusinessException;
import com.example.secaicontainerengine.pojo.dto.file.UploadFileRequest;
import com.example.secaicontainerengine.pojo.dto.model.ModelConfig;
import com.example.secaicontainerengine.pojo.dto.model.ResourceConfig;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.pojo.entity.User;
import com.example.secaicontainerengine.pojo.enums.FileUploadBizEnum;
import com.example.secaicontainerengine.service.User.UserService;
import com.example.secaicontainerengine.service.modelmessage.ModelMessageService;
import com.example.secaicontainerengine.util.ThrowUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;

import static com.example.secaicontainerengine.util.FileUtils.processFilesInDirectory;
import static com.example.secaicontainerengine.util.FileUtils.unzipFile;

@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    @Autowired
    private ModelMessageService modelMessageService;


    /**
     * 文件上传，并解压文件，同时将解压后的模型的相关文件的地址保存到数据表model_message中
     *
     * @param multipartFile
     * @param uploadFileRequest
     * @param request
     * @return
     */
    @PostMapping("/upload")
    public BaseResponse<Long> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                         UploadFileRequest uploadFileRequest, HttpServletRequest request) {

        //TODO 这里的通过swagger自动生成的测试接口测试uploadFileRequest为空不知道为什么，初步判定是openapi的有bug，也可能是使用方法不对
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validFile(multipartFile, fileUploadBizEnum);
        User loginUser = userService.getLoginUser(request);
        // 文件目录：根据业务、用户来划分
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + multipartFile.getOriginalFilename();
        String filepath = String.format("/%s/%s/%s", fileUploadBizEnum.getValue(), loginUser.getId(), filename);
//        String filepath = String.format("/%s/%s", fileUploadBizEnum.getValue(), filename);
        filepath = FileConstant.FILE_BASE_PATH + filepath;
        File file = new File(filepath);
        // 创建目录
        File parentDirectory = file.getParentFile(); // 获取父目录
        if (!parentDirectory.exists()) {
            boolean created = parentDirectory.mkdirs(); // 创建所有父目录
            if (!created) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法创建文件目录");
            }
        }
        try {
            multipartFile.transferTo(file);
            String model_save_path = unzipFile(filepath, parentDirectory.getAbsolutePath());
            ModelMessage modelMessage = processFilesInDirectory(model_save_path);
            modelMessage.setUserId(loginUser.getId());
            ModelConfig modelConfig = uploadFileRequest.getModelConfig();
            if(modelConfig != null) {
                modelMessage.setModelConfig(JSONUtil.toJsonStr(modelConfig));
            }
            ResourceConfig resourceConfig = uploadFileRequest.getResourceConfig();
            if(resourceConfig != null) {
                modelMessage.setResourceConfig(JSONUtil.toJsonStr(resourceConfig));
            }
            // 更新数据库
            modelMessage.setAllDataAddress(model_save_path);
            modelMessage.setUserId(loginUser.getId());
            boolean result = modelMessageService.save(modelMessage);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            Long newModelMessageId = modelMessage.getId();
            return ResultUtils.success(newModelMessageId);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
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
        if (FileUploadBizEnum.MODEL_DATA.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 2G");
            }
            if (!Arrays.asList("zip","7z").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        }
    }
}
