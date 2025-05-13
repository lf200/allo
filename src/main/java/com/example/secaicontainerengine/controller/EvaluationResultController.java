package com.example.secaicontainerengine.controller;

import com.example.secaicontainerengine.common.BaseResponse;
import com.example.secaicontainerengine.pojo.dto.model.EvaluationMethodRequest;
import com.example.secaicontainerengine.service.modelEvaluation.EvaluationResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/evaluate/result")
public class EvaluationResultController {

    @Autowired
    private EvaluationResultService evaluationResultService;

    @PostMapping("/update")
    public void calculateAndUpdateScores(@RequestParam("modelId") Long modelId) {

        evaluationResultService.calculateAndUpdateScores(modelId);
    }

}
