package com.example.secaicontainerengine.controller;


import com.example.secaicontainerengine.common.BaseResponse;
import com.example.secaicontainerengine.common.ResultUtils;
import com.example.secaicontainerengine.pojo.dto.model.EvaluationMethodRequest;
import com.example.secaicontainerengine.pojo.entity.Log;
import com.example.secaicontainerengine.service.modelEvaluation.EvaluationMethodService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/method")
public class EvaluationMethodController {


    @Autowired
    private EvaluationMethodService evaluationMethodService;

    @PostMapping("/insert")
    public BaseResponse<?> insertEvaluationMethod(@RequestBody EvaluationMethodRequest evaluationMethodRequest) {
        return evaluationMethodService.insert(evaluationMethodRequest);
    }
}
