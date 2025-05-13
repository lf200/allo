package com.example.secaicontainerengine.service.modelEvaluation;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.secaicontainerengine.mapper.ModelMessageMapper;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ModelMessageServiceImpl extends ServiceImpl<ModelMessageMapper, ModelMessage> implements ModelMessageService {
}
