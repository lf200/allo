package com.example.secaicontainerengine.service.container;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.mapper.ContainerMapper;
import com.example.secaicontainerengine.pojo.entity.Container;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

public class VirtualMachineImpl implements ContainerService {

    public List<ByteArrayInputStream> init(String userId, Map<String, String> imageUrl, Map<String, Map> imageParam){
        return null;
    }

    @Override
    public void start(String userId, List<ByteArrayInputStream> streams){

    }

    @Override
    public void deleteAll(String userId) {

    }

    @Override
    public void deleteSingle(String userId, String podName) {

    }


}
