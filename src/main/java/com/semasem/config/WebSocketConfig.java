package com.semasem.config;

import com.semasem.controller.WebRTCController;
import com.semasem.controller.WebSocketChatController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebRTCController webRTCController;
    private final WebSocketChatController webSocketChatController;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // WebRTC endpoint
        registry.addHandler(webRTCController, "/api/ws/webrtc")
                .setAllowedOriginPatterns("*");

        // Chat endpoint
        registry.addHandler(webSocketChatController, "/api/ws/chat")
                .setAllowedOriginPatterns("*");

        log.info("✅ WebSocket endpoints registered");
    }
}