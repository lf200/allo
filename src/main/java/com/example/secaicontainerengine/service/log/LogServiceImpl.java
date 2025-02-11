package com.example.secaicontainerengine.service.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.secaicontainerengine.pojo.entity.Log;
import com.example.secaicontainerengine.mapper.LogMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * <p>
 * 容器运行过程中产生的日志 服务实现类
 * </p>
 *
 * @author CFZ
 * @since 2025-02-11
 */
@Service
@Slf4j
public class LogServiceImpl extends ServiceImpl<LogMapper, Log> implements ILogService {

    @Value("${es.name}")
    private String esName;

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private LogMapper logMapper;

    @Override
    public String getLogByMysql(String containerName, String messageKey) {
        Log result = logMapper.selectOne(
                new LambdaQueryWrapper<Log>()
                        .eq(Log::getPodName, containerName)
                        .eq(Log::getMessageKey, messageKey)
                        .orderByDesc(Log::getLogTime)
                        .last("LIMIT 1")
        );
        return result != null ? result.getMessageValue() : null;
    }

    @Override
    public String getLogByES(String containerName, String messageKey) throws IOException {
        SearchRequest request = new SearchRequest(esName);
        request.source().query(QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("pod_name", containerName))
                .must(QueryBuilders.termQuery("message_key", messageKey)));
        request.source().sort("@timestamp", SortOrder.DESC);
        request.source().size(1);
        SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
        SearchHits searchHits = response.getHits();
        SearchHit[] hits = searchHits.getHits();
        if(hits == null || hits.length == 0) {
            log.info("该容器还未产生日志或者不存在该容器");
            return null;
        }
        Map<String, Object> sourceAsMap = hits[0].getSourceAsMap();
        Object messageValue = sourceAsMap.get("message_value");
        return messageValue.toString();
    }

    @Override
    public boolean saveLog(Log log) {
        return save(log);
    }
}
