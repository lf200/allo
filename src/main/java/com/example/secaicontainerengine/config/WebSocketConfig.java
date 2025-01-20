package com.example.secaicontainerengine.config;
import com.example.secaicontainerengine.handler.UploadStatusWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UploadStatusWebSocketHandler uploadStatusWebSocketHandler;

    public WebSocketConfig(UploadStatusWebSocketHandler uploadStatusWebSocketHandler) {
        this.uploadStatusWebSocketHandler = uploadStatusWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册WebSocket处理器，设置路径为"/upload-status"，随意起
        registry.addHandler(uploadStatusWebSocketHandler, "/upload-progress")
                .setAllowedOrigins("*"); // 允许跨域访问
    }
}
