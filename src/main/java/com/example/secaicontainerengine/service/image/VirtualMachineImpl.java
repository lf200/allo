package com.example.secaicontainerengine.service.image;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.mapper.ImageMapper;
import com.example.secaicontainerengine.pojo.entity.Image;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

public class VirtualMachineImpl implements ImageService{
    @Override
    public Map<String, String> getUrlByName(List<String> containers) {
        return Map.of();
    }
}
