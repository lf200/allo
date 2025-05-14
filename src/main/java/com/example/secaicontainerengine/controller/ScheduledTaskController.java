package com.example.secaicontainerengine.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.secaicontainerengine.common.BaseResponse;
import com.example.secaicontainerengine.common.ResultUtils;
import com.example.secaicontainerengine.exception.BusinessException;
import com.example.secaicontainerengine.pojo.entity.Container;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.service.container.ContainerService;
import com.example.secaicontainerengine.service.modelEvaluation.EvaluationResultService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelEvaluationService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.example.secaicontainerengine.common.ErrorCode.SYSTEM_ERROR;

/**
 * <p>
 * 调度任务接口，给若依平台调用
 * </p>
 *
 * @author CFZ
 * @since 2025-05-08
 */
@Slf4j
@RestController
@RequestMapping("/scheduled-task")
public class ScheduledTaskController {

    @Autowired
    private ExecutorService taskExecutor;

    @Autowired
    private ModelMessageService modelMessageService;

    @Autowired
    private ModelEvaluationService modelEvaluationService;

    @Autowired
    private ContainerService containerService;

    @Autowired
    private EvaluationResultService evaluationResultService;


    /**
     * 启动与modelId关联的评测任务
     * @param modelId
     * @return
     */
    @PostMapping
    public BaseResponse<?> startEvaluation(@RequestBody Long modelId){
        ModelMessage modelMessage = modelMessageService.getById(modelId);
        // 使用新线程异步执行任务
        taskExecutor.submit(() -> {
            try {
                // 根据 BusinessConfig 配置启动对应的 Pod
                modelEvaluationService.startEvaluationPod(modelMessage);

            } catch (Exception e) {
                // 处理异常，捕获可能发生的错误
                throw new BusinessException(SYSTEM_ERROR, e.getMessage());
            }
        });
        // 立即返回响应，告诉前端评测任务已启动
        return ResultUtils.success("系统正在评测中...");
    }

    /**
     * 返回与modelId关联的Pod是否全部执行完毕
     * @param modelId
     * @return 0代表还有Pod没有执行完毕，1代表Pod全部执行成功，2代表有Pod执行失败
     */
    @GetMapping
    public int allFinished(@RequestParam Long modelId){
        // 2.1检查所有容器是否都已经完成
        List<Container> containers = containerService.list(
                new QueryWrapper<Container>().eq("modelId", modelId)
        );
        boolean allFinished = containers.stream()
                .allMatch(container -> {
                    String status = container.getStatus();
                    return "Succeed".equalsIgnoreCase(status) || "Failed".equalsIgnoreCase(status);
                });
        if(!allFinished){
            return 0;
        }
        List<Long> ids = containerService.list(
                new QueryWrapper<Container>()
                        .eq("modelId", modelId)
                        .eq("status", "Failed")
        ).stream().map(Container::getId).toList();
        if(ids.isEmpty()){
            // 修改模型状态为评测成功
            modelMessageService.update(
                    new LambdaUpdateWrapper<ModelMessage>()
                            .eq(ModelMessage::getId, modelId)
                            .set(ModelMessage::getStatus, 3)
            );
            return 1;
        }else {
            //修改模型状态为评测失败
            modelMessageService.update(
                    new LambdaUpdateWrapper<ModelMessage>()
                            .eq(ModelMessage::getId, modelId)
                            .set(ModelMessage::getStatus, 4)
            );
            return 2;
        }
    }

    /**
     * 计算modelId的评测结果
     * @param modelId
     */
    @PostMapping("/result")
    public void computeResult(@RequestBody Long modelId){
        evaluationResultService.calculateAndUpdateScores(modelId);
    }




}
