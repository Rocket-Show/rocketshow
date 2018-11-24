package com.ascargon.rocketshow.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@Controller
class WebSocketConfig implements WebSocketConfigurer {

    private final DefaultNotificationService webSocketClientNotifier;

    public WebSocketConfig(DefaultNotificationService webSocketClientNotifier) {
        this.webSocketClientNotifier = webSocketClientNotifier;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketClientNotifier, "/api/state").setAllowedOrigins("*");
    }

}