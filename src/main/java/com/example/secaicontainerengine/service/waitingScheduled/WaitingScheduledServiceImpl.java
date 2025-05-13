package com.example.secaicontainerengine.service.waitingScheduled;

import com.example.secaicontainerengine.pojo.entity.WaitingScheduled;
import com.example.secaicontainerengine.mapper.WaitingScheduledMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 调度任务表，用于保存等待被调度的任务 服务实现类
 * </p>
 *
 * @author CFZ
 * @since 2025-05-13
 */
@Service
public class WaitingScheduledServiceImpl extends ServiceImpl<WaitingScheduledMapper, WaitingScheduled> implements WaitingScheduledService {

}
