package com.example.secaicontainerengine.service.container;

import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import freemarker.template.TemplateException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface ContainerService {

    //初始化接口
    List<ByteArrayInputStream> init(String userId, Map<String, String> imageUrl, Map<String, Map> imageParam) throws IOException, TemplateException;

    List<ByteArrayInputStream> initNew(ModelMessage modelMessage, List<String> podYamlFile, String modelName) throws IOException, TemplateException;

    //启动接口
    void start(String userId, List<ByteArrayInputStream> streams) throws IOException;

    //回收接口1-删除指定用户的所有容器
    void deleteAll(String userId);

    //回收接口2-删除用户的单个容器
    void deleteSingle(String userId, String containerName);

    //监听接口1-持续监听指定的pod状态
    void watchStatus(String userId, String containerName);

    /**
     * 监听接口2：只输出一次容器的状态
     * @param containerName 容器名称
     * @return 代表容器状态的字符串
     */
    String getStatus(String containerName);


}
