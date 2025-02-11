package com.example.secaicontainerengine.service.log;

import com.example.secaicontainerengine.pojo.entity.Log;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;

/**
 * <p>
 * 容器运行过程中产生的日志 服务类
 * </p>
 *
 * @author CFZ
 * @since 2025-02-11
 */
public interface ILogService extends IService<Log> {

    String getLogByMysql(String containerName, String messageKey);

    String getLogByES(String containerName, String messageKey) throws IOException;

    boolean saveLog(Log log);
}
