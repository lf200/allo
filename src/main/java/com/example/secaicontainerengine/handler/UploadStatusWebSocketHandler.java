package com.example.secaicontainerengine.handler;


import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class UploadStatusWebSocketHandler extends TextWebSocketHandler {

    // 存储 userId 与 WebSocketSession 的映射
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getSessionIdFromSession(session);
        if (userId != null) {
            sessionMap.put(userId, session);
            System.out.println("------------WebSocket connection established with userId: " + userId);
        } else {
            System.out.println("------------WebSocket connection established without userId");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getSessionIdFromSession(session);
        if (userId != null) {
            sessionMap.remove(userId);
            System.out.println("----------------WebSocket connection closed for userId: " + userId);
        }
    }

    // 入参随意，有userId就行，这方法是主动返回给前端才调用的
    public void sendUploadStatus(String userId, String message) {
        WebSocketSession session = sessionMap.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                System.out.println(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("--------------Session not found or closed for userId: " + userId);
        }
    }

    // 从 WebSocketSession 中提取 sessionId 参数
    private String getSessionIdFromSession(WebSocketSession session) {
        URI uri = session.getUri();
        String query = uri.getQuery();
        if (query != null && query.contains("userId=")) {
            return query.split("userId=")[1];
        }
        return null;
    }
}
