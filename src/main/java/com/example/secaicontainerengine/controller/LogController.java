package com.example.secaicontainerengine.controller;


import com.example.secaicontainerengine.pojo.entity.Log;
import com.example.secaicontainerengine.pojo.vo.LogVO;
import com.example.secaicontainerengine.service.log.LogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 容器运行过程中产生的日志 前端控制器
 * </p>
 *
 * @author CFZ
 * @since 2025-02-19
 */
@Slf4j
@RestController
@RequestMapping("/log")
public class LogController {

    @Autowired
    private LogService logService;

    @PostMapping
    public int saveLog(@RequestBody Log newlog) {
        log.info("插入日志："+newlog);
        return logService.saveLog(newlog);
    }

    @GetMapping("/container/latest")
    public LogVO getLatestLogByContainerName(String containerName, String messageKey) {
        log.info("查询日志，容器名称："+containerName+", "+messageKey);
        return logService.getLatestLogByMysql(containerName, messageKey);
    }

    @GetMapping("/container/all")
    public List<LogVO> getAllLogByContainerName(String containerName) {
        log.info("查询日志，容器名称："+containerName);
        return logService.getAllLogByMysql(containerName);
    }

    @GetMapping("/model/latest")
    public Map<String, LogVO> getLatestLogByModelId(Long modelId, String messageKey) {
        log.info("查询日志，模型id："+modelId+", "+messageKey);
        return logService.getLatestLogByModelId(modelId, messageKey);
    }

    @GetMapping("/model/all")
    public Map<String, List<LogVO>> getAllLogByModelId(Long modelId) {
        log.info("查询日志，模型id："+modelId);
        return logService.getAllLogByModelId(modelId);
    }

//    @DeleteMapping("/container/{containerName}")
//    public void deleteLogByContainerName(@PathVariable String containerName) {
//        log.info("删除该容器的所有日志: "+containerName);
//        logService.deleteByContainerName(containerName);
//    }
//
//    @DeleteMapping("/model/{modelId}")
//    public void deleteLogByModelId(@PathVariable Long modelId) {
//        log.info("删除该模型的所有日志: "+modelId);
//        logService.deleteByModelId(modelId);
//    }
//
//    @DeleteMapping("/time/{lastTime}")
//    public void deleteLogByLastTime(@PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastTime) {
//        log.info("删除该时间之前的所有日志: "+lastTime);
//        logService.deleteByTime(lastTime);
//    }



}
