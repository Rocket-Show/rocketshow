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

    private final DefaultNotificationService defaultNotificationService;
    private final DefaultActivityNotificationMidiService defaultActivityNotificationMidiService;

    public WebSocketConfig(DefaultNotificationService defaultNotificationService, DefaultActivityNotificationMidiService defaultActivityNotificationMidiService) {
        this.defaultNotificationService = defaultNotificationService;
        this.defaultActivityNotificationMidiService = defaultActivityNotificationMidiService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(defaultNotificationService, "/api/state").setAllowedOrigins("*");
        registry.addHandler(defaultActivityNotificationMidiService, "/api/activity/midi").setAllowedOrigins("*");
    }

}