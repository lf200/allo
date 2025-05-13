package com.example.secaicontainerengine.controller;


import com.example.secaicontainerengine.pojo.dto.RequestDTO;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.service.container.ContainerService;
import com.example.secaicontainerengine.service.image.ImageService;
import com.example.secaicontainerengine.util.RequestDTOUtil;
import freemarker.template.TemplateException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/container")
@Slf4j
public class ContainerController {

    @Autowired
    private ContainerService containerService;
    @Autowired
    private ImageService imageService;


//    @PostMapping("/start")
//    public String start(@RequestBody RequestDTO requestDTO) throws TemplateException, IOException {
//        //获取请求的用户id
//        Long userId = requestDTO.getUserId();
//        //获取请求的业务列表
//        List<String> imagesName = RequestDTOUtil.getImagesName(requestDTO);
//        //获取每个镜像的参数
//        Map imageParam = RequestDTOUtil.getImagesParam(requestDTO);
//        //获取每个镜像的资源限制
//        Map resourceLimit = RequestDTOUtil.getResourceLimit(requestDTO);
//        //获取每个镜像的拉取地址
//        Map<String, String> imageUrl = imageService.getUrlByName(imagesName);
//
//        log.info("userId = {}", userId);
//        log.info("imagesName = {}", imagesName);
//        log.info("imageParam = {}", imageParam);
//        log.info("resourceLimit = {}", resourceLimit);
//        log.info("imageUrl = {}", imageUrl);
//
//        List streams = containerService.init(userId, imageUrl, imageParam);
//        containerService.start(userId, 31441L, streams);
//        return "请求成功";
//    }

    @DeleteMapping("/delete/{userId}")
    public void delete(@PathVariable Long userId) {
        log.info("开始回收用户{}的容器：", userId);
        containerService.deleteAll(userId);
    }

    @GetMapping("/{modelId}")
    public List<String> getContainersByModelId(@PathVariable Long modelId){
        return containerService.getContainersByModelId(modelId);
    }

    @GetMapping("/monitor/{containerName}")
    public String monitor(@PathVariable String containerName) {
        return containerService.getStatus(containerName);
    }

    

}
