package com.example.secaicontainerengine.controller;


import com.example.secaicontainerengine.pojo.entity.Log;
import com.example.secaicontainerengine.service.log.ILogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * <p>
 * 容器运行过程中产生的日志 前端控制器
 * </p>
 *
 * @author CFZ
 * @since 2025-02-11
 */
@RestController
@RequestMapping("/log")
public class LogController {

    @Autowired
    private ILogService logService;

    @GetMapping
    public String getLog(String containerName, String messageKey, String source) throws IOException {
        if(source!=null && source.equals("mysql")){
            return logService.getLogByMysql(containerName,messageKey);
        }
        return logService.getLogByES(containerName, messageKey);
    }

    @PostMapping
    public boolean saveLog(@RequestBody Log log) {
        return logService.saveLog(log);
    }
}
