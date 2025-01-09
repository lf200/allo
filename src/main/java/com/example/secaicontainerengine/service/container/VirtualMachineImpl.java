package com.example.secaicontainerengine.service.container;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.mapper.ContainerMapper;
import com.example.secaicontainerengine.pojo.entity.Container;
import freemarker.template.TemplateException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class VirtualMachineImpl implements ContainerService {


    @Override
    public List<ByteArrayInputStream> init(String userId, Map<String, String> imageUrl, Map<String, Map> imageParam) throws IOException, TemplateException {
        return List.of();
    }

    @Override
    public void start(String userId, List<ByteArrayInputStream> streams) throws IOException {

    }

    @Override
    public void deleteAll(String userId) {

    }

    @Override
    public void deleteSingle(String userId, String containerName) {

    }

    @Override
    public void watchStatus(String userId, String containerName) {

    }

    @Override
    public String getStatus(String containerName) {
        return "";
    }

}
