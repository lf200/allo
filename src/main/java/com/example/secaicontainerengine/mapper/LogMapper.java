package com.example.secaicontainerengine.mapper;

import com.example.secaicontainerengine.pojo.entity.Log;
import com.example.secaicontainerengine.pojo.vo.LogVO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 容器运行过程中产生的日志 Mapper 接口
 * </p>
 *
 * @author CFZ
 * @since 2025-02-19
 */
@Mapper
public interface LogMapper{

    @Insert("INSERT INTO log(modelId, containerName, evaluateDimension, evaluateMetric, messageKey, messageValue) " +
            "VALUE(#{modelId}, #{containerName}, #{evaluateDimension}, #{evaluateMetric} ,#{messageKey}, #{messageValue})")
    int insert(Log log);

    @Select("SELECT messageKey, messageValue, logTime from log WHERE containerName = #{containerName} AND messageKey = #{messageKey} " +
            "ORDER BY logTime DESC LIMIT 1")
    LogVO getLatestMessageValue(String containerName, String messageKey);

    @Select("SELECT messageKey,  messageValue, logTime, messageKey " +
            "FROM (" +
            "    SELECT messageValue, logTime, messageKey, " +
            "           ROW_NUMBER() OVER (PARTITION BY messageKey ORDER BY logTime DESC) AS rn " +
            "    FROM log " +
            "    WHERE containerName = #{containerName} " +
            ") AS ranked " +
            "WHERE rn = 1")
    List<LogVO> getAllMessageValue(String containerName);

    void deleteByContainers(List<String> containers);

    @Delete("DELETE FROM log where containerName = #{containerName}")
    void deleteByContainer(String containerName);

    @Delete("DELETE FROM log where logTime < #{lastTime}")
    void deleteByTime(LocalDateTime lastTime);


}
