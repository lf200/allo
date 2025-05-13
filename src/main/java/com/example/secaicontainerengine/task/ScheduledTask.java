package com.example.secaicontainerengine.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.secaicontainerengine.pojo.entity.Container;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.pojo.entity.ScheduledTable;
import com.example.secaicontainerengine.service.container.ContainerService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelEvaluationService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelMessageService;
import com.example.secaicontainerengine.service.scheduledTable.ScheduledTableService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ScheduledTask {

    @Autowired
    private ScheduledTableService scheduledTableService;

    @Autowired
    private ContainerService containerService;

    @Autowired
    private ModelMessageService modelMessageService;

    //每5秒扫描一次需要进行评测的模型任务
    @Scheduled(cron = "*/5 * * * * *")
    public void startEvaluation(){
        // 首先从数据表中查询出当前需要评测的模型id
        List<Long> modelIds = scheduledTableService.getModelIdByStatus();
        log.info("本次被调度的任务：" + modelIds);
        // 依次启动每个模型的任务
        for (Long modelId : modelIds) {
            //调用评测启动接口

            //修改容器状态为评测中
            modelMessageService.update(
                    new UpdateWrapper<ModelMessage>()
                            .eq("modelId", modelId)
                            .set("status", 2)
            );
        }
    }

    //每5秒扫描一次是否有模型的评测任务已完成，修改其状态为成功或者失败
    @Scheduled(cron = "*/5 * * * * *")
    public void modifyStatus(){
        // 首先查询出已经评测完毕的模型id
        List<Long> modelIds = scheduledTableService.getFinishedModelId();
        log.info("已评测完毕的任务：" + modelIds);
        // 如果所有容器执行成功，就把模型的状态修改为成功，否则修改为失败
        for (Long modelId : modelIds) {
            List<Long> ids = containerService.list(
                    new QueryWrapper<Container>()
                            .eq("modelId", modelId)
                            .eq("status", "Failed")
            ).stream().map(Container::getId).toList();
            if(!ids.isEmpty()){
                //修改调度状态为评测成功
                scheduledTableService.update(
                        new UpdateWrapper<ScheduledTable>()
                                .eq("modelId", modelId)
                                .set("status", 2)
                );
            }else {
                //修改调度状态为评测失败
                scheduledTableService.update(
                        new UpdateWrapper<ScheduledTable>()
                                .eq("modelId", modelId)
                                .set("status", 3)
                );
            }
        }
    }

    //每5秒扫描一次状态为成功的评测任务，计算其结果
    @Scheduled(cron = "*/5 * * * * *")
    public void computeResult(){
        // 首先查询出状态评测成功的modelId
        List<Long> modelIds = scheduledTableService.list(
                new QueryWrapper<ScheduledTable>().eq("status", 2)
        ).stream().map(ScheduledTable::getModelId).toList();
        // 依次计算每个modelId的评测结果
        if(!modelIds.isEmpty()){
            log.info("本次计算评测结果的modelId：" + modelIds);
            for (Long modelId : modelIds) {
                //调用计算结果接口

                //修改调度状态为已计算结果
                scheduledTableService.update(
                        new UpdateWrapper<ScheduledTable>()
                                .eq("modelId", modelId)
                                .set("status", 4)
                );
            }
        }
    }

}
