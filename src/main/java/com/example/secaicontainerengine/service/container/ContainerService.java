package com.example.secaicontainerengine.service.container;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.secaicontainerengine.pojo.entity.Container;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import freemarker.template.TemplateException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ContainerService extends IService<Container> {

    //初始化接口
    List<ByteArrayInputStream> init(Long userId, Map<String, String> imageUrl, Map<String, Map> imageParam) throws IOException, TemplateException;

    List<ByteArrayInputStream> initNew(ModelMessage modelMessage, List<String> podYamlFile, String modelName) throws IOException, TemplateException;

    //启动接口
    void start(Long userId, Long modelId, List<ByteArrayInputStream> streams) throws IOException;

    //回收接口1-删除指定用户的所有容器
    void deleteAll(Long userId);

    //回收接口2-删除用户的单个容器
    void deleteSingle(Long userId, String containerName);

    /**
     * 监听接口1：持续监听指定的容器状态
     * @param userId 用户id
     * @param containerName 容器名称
     */
    void watchStatus(Long userId, Long modelId, String containerName);

    /**
     * 监听接口2：只输出一次容器的状态
     * @param containerName 容器名称
     * @return 代表容器状态的字符串
     */
    String getStatus(String containerName);


}
