package com.ruoyi.quartz.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.secaicontainerengine.pojo.entity.Container;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.service.container.ContainerService;
import com.ruoyi.common.exception.job.TaskException;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.service.ISysJobService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;


@Component("evaluateTask")
@Slf4j
public class EvaluateTask {

    @Autowired
    private ISysJobService jobService;

    @Autowired
    private ContainerService containerService;

    @Autowired
    private WaitingScheduledService waitingScheduledService;

    @Autowired
    private ModelMessageService modelMessageService;


    //评测任务启动
    public void startEvaluation(Long modelId) throws SchedulerException, TaskException {
        log.info("本次调度操作的评测模型id：{}", modelId);
        SysJob evaluateTask = jobService.getByModelId(modelId);
        //---------------1.检查评测任务是否已经启动--------------
        // 1.1首先检查传入的评测任务在调度表中是否已存在，如果不存在，
        if(evaluateTask==null){
            // 1.2调用评测启动接口

            // 1.3往调度表中插入一条记录
            SysJob job=new SysJob();
            job.setModelid(modelId);
            job.setEvaluate_status("1");
            jobService.insertJob(job);
            // 1.4删除用户评测请求表中的记录
            waitingScheduledService.remove(
                    new LambdaQueryWrapper<WaitingScheduled>()
                            .eq(WaitingScheduled::getModelId, modelId)
            );
            // 1.5修改模型状态为评测中
            modelMessageService.update(
                    new LambdaUpdateWrapper<ModelMessage>()
                            .eq(ModelMessage::getId, modelId)
                            .set(ModelMessage::getStatus, 2)
            );
            //结束本次调度
            return;
        }

        //---------------2.检查评测任务是否执行完成--------------
        if(evaluateTask.getEvaluate_status().equals("1")){
            // 2.1检查所有容器是否都已经完成
            List<Container> containers = containerService.list(
                    new QueryWrapper<Container>().eq("modelId", modelId)
            );
            boolean allFinished = containers.stream()
                    .allMatch(container -> {
                        String status = container.getStatus();
                        return "Succeed".equalsIgnoreCase(status) || "Failed".equalsIgnoreCase(status);
                    });
            // 2.2如果还未运行完毕，退出本次调度
            if(!allFinished){
                return;
            }
            List<Long> ids = containerService.list(
                    new QueryWrapper<Container>()
                            .eq("modelId", modelId)
                            .eq("status", "Failed")
            ).stream().map(Container::getId).collect(Collectors.toList());
            if(ids.isEmpty()){
                // 2.3修改调度状态为评测成功
                evaluateTask.setEvaluate_status("2");
                // 修改模型状态为评测成功
                modelMessageService.update(
                        new LambdaUpdateWrapper<ModelMessage>()
                                .eq(ModelMessage::getId, modelId)
                                .set(ModelMessage::getStatus, 3)
                );
            }else {
                // 2.4修改调度状态为评测失败
                evaluateTask.setEvaluate_status("3");
                //修改模型状态为评测失败
                modelMessageService.update(
                        new LambdaUpdateWrapper<ModelMessage>()
                                .eq(ModelMessage::getId, modelId)
                                .set(ModelMessage::getStatus, 4)
                );
            }
        }

        //---------------3.计算评测结果--------------
        if(evaluateTask.getEvaluate_status().equals("2")){
            //调用计算评测结果接口

            evaluateTask.setEvaluate_status("4");
        }
        jobService.updateJob(evaluateTask);
        //暂停当前调度任务
        evaluateTask.setStatus("1");
        jobService.changeStatus(evaluateTask);
        log.info("评测模型id：{}的调度任务已被暂停", modelId);
    }


}
