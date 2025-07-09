package com.example.secaicontainerengine.controller;

import com.example.secaicontainerengine.config.SftpUploader;
import com.example.secaicontainerengine.pojo.dto.result.EvaluationRequest;
import com.example.secaicontainerengine.pojo.dto.result.EvaluationStatus;
import com.example.secaicontainerengine.service.modelEvaluation.EvaluationResultService;
import com.example.secaicontainerengine.service.modelEvaluation.ModelEvaluationService;
import com.jcraft.jsch.ChannelSftp;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

@Slf4j
@RestController
@RequestMapping("/evaluate/result")
public class EvaluationResultController {

    @Autowired
    private EvaluationResultService evaluationResultService;

    @Autowired
    private ModelEvaluationService modelEvaluationService;

    @Autowired
    private SftpUploader sftpUploader;

    @PostMapping("/update")
    public void calculateAndUpdateScores(@RequestParam("modelId") Long modelId) {
        evaluationResultService.calculateAndUpdateScores(modelId);
    }

    @PostMapping
    public void updateResult(@RequestBody EvaluationRequest request){
        log.error("评测结果"+request);
        evaluationResultService.updateResult(request.getModelId(), request.getResult(), request.getResultColumn());
    }

    @PostMapping("/status")
    public void updateStatus(@RequestBody EvaluationStatus evaluationStatus){
        log.error("评测状态："+evaluationStatus);
        evaluationResultService.updateStatus(evaluationStatus);
    }

    @GetMapping("/image")
    public ResponseEntity<?> downloadImage(@RequestParam String imagePath) {
        ChannelSftp channelSftp=null;
        try {
            channelSftp = sftpUploader.connectNfs().getSftpChannel();
            channelSftp.connect();

            try (InputStream inputStream = channelSftp.get(imagePath)) {
                byte[] bytes = IOUtils.toByteArray(inputStream);
                ByteArrayResource resource = new ByteArrayResource(bytes);

                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"image.png\"")
                        .body(resource);
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("SFTP 下载失败：" + e.getMessage());
        } finally {
            if (channelSftp != null) channelSftp.exit();
        }
    }

    @GetMapping
    public ResponseEntity<?> getResult(@RequestParam String modelId, @RequestParam String evaluateDimension){
        String result = modelEvaluationService.getResult(modelId, evaluateDimension+"Result");
        return ResponseEntity.ok(result);
    }

}
