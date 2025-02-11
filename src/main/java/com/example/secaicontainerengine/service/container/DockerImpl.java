package com.example.secaicontainerengine.service.container;

import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import freemarker.template.TemplateException;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.mapper.ContainerMapper;
import com.example.secaicontainerengine.pojo.entity.Container;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DockerImpl extends ServiceImpl<ContainerMapper, Container> implements ContainerService {


    @Override
    public List<ByteArrayInputStream> init(Long userId, Map<String, String> imageUrl, Map<String, Map> imageParam) throws IOException, TemplateException {
        return List.of();
    }

    @Override
    public List<ByteArrayInputStream> initNew(ModelMessage modelMessage, List<String> podYamlFile, String modelName) throws IOException, TemplateException {
        return List.of();
    }


    @Override
    public void start(Long userId,  Long modelId,List<ByteArrayInputStream> streams) throws IOException {

    }

    @Override
    public void deleteAll(Long userId) {

    }

    @Override
    public void deleteSingle(Long userId, String containerName) {

    }

    @Override
    public void watchStatus(Long userId, Long modelId, String containerName) {

    }

    @Override
    public String getStatus(String containerName) {
        return "";
    }

}
