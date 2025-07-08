package com.example.secaicontainerengine.controller;


import com.example.secaicontainerengine.common.BaseResponse;
import com.example.secaicontainerengine.common.ResultUtils;
import com.example.secaicontainerengine.exception.BusinessException;
import com.example.secaicontainerengine.pojo.dto.model.ModelEvaluationRequest;
import com.example.secaicontainerengine.pojo.entity.ModelMessage;
import com.example.secaicontainerengine.pojo.vo.ModelEvaluation.GenerateReport;
import com.example.secaicontainerengine.service.modelEvaluation.EvaluationResultService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelEvaluationService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelMessageService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.io.IOException;
import java.util.concurrent.ExecutorService;

import static com.example.secaicontainerengine.common.ErrorCode.SYSTEM_ERROR;

@RestController
@RequestMapping("/api/evaluation")
@Slf4j
public class ModelEvaluationController {

    @Autowired
    private ModelMessageService modelMessageService;

    @Autowired
    private ModelEvaluationService modelEvaluationService;

    @Autowired
    private EvaluationResultService evaluationResultService;

    @Autowired
    private ExecutorService taskExecutor;

    @Autowired
    private OkHttpClient okHttpClient;



    @PostMapping("/start/{modelId}")
//    public BaseResponse<?> startModelEvaluation(@RequestBody ModelEvaluationRequest modelEvaluationRequest) {
    public BaseResponse<?> startModelEvaluation(@PathVariable Long modelId) {
//        Long modelId = modelEvaluationRequest.getModelId();

//         测试

//        modelId = 1889145615706112001L;


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

    @PostMapping("/result/{modelId}")
    public GenerateReport getResultReport(@PathVariable Long modelId){
        GenerateReport resultReport = evaluationResultService.getResultReport(modelId);
        return resultReport;
    }

    // 任务调度方式启动
    @PostMapping("/startNew")
    public BaseResponse<?> startModelEvaluationNew(@RequestBody ModelEvaluationRequest modelEvaluationRequest){
        Long modelId = modelEvaluationRequest.getModelId();
        ModelMessage modelMessage = modelMessageService.getById(modelId);
        if(modelMessage==null){
            return ResultUtils.error(4000, "不存在该modelId");
        }
        switch (modelMessage.getStatus()){
            // 0在数据库中代表还未上传至nfs服务器
            case 0:
                return ResultUtils.error(4001,"数据正在上传至nfs服务器，请稍后再试");
            // 1在数据库中代表已经上传至nfs服务器
            case 1:
                //创建一个调度任务
                String job=String.format(
                        "{concurrent:1, status:0, jobGroup:\"DEFAULT\", misfirePolicy:1, cronExpression:\"0/10 * * * * ?\"" +
                                "invokeTarget:\"evaluateTask.startEvaluation(\"%s\")\", jobName:\"%s\"}", modelId, modelId
                );
                okhttp3.RequestBody body = okhttp3.RequestBody.create(job, MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url("http://10.195.34.44:8080/monitor/job")
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();
                log.info("**************************88");
                try {
                    Response response = okHttpClient.newCall(request).execute();
                    log.info("**************************88");
                    log.info(String.valueOf(response.body()));
                    log.info("**************************77");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            // 2代表该模型在评测中
            case 2:
                return ResultUtils.error(4002, "该modelId正在评测中");
            // 3代表该模型已评测成功
            case 3:
                return ResultUtils.error(4003, "该modelId已评测成功");
            // 4代表该模型已评测失败
            case 4:
                return ResultUtils.error(4004, "该modelId已评测失败");
        }
        return ResultUtils.success("该modelId成功加入任务调度队列");
    }


}
