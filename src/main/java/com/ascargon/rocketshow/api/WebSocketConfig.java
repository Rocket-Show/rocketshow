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
    private final DefaultActivityNotificationAudioService defaultActivityNotificationAudioService;
    private final DefaultActivityNotificationLightingService defaultActivityNotificationLightingService;

    public WebSocketConfig(DefaultNotificationService defaultNotificationService, DefaultActivityNotificationMidiService defaultActivityNotificationMidiService, DefaultActivityNotificationAudioService defaultActivityNotificationAudioService, DefaultActivityNotificationLightingService defaultActivityNotificationLightingService) {
        this.defaultNotificationService = defaultNotificationService;
        this.defaultActivityNotificationMidiService = defaultActivityNotificationMidiService;
        this.defaultActivityNotificationAudioService = defaultActivityNotificationAudioService;
        this.defaultActivityNotificationLightingService = defaultActivityNotificationLightingService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(defaultNotificationService, "/api/state").setAllowedOrigins("*");
        registry.addHandler(defaultActivityNotificationMidiService, "/api/activity/midi").setAllowedOrigins("*");
        registry.addHandler(defaultActivityNotificationAudioService, "/api/activity/audio").setAllowedOrigins("*");
        registry.addHandler(defaultActivityNotificationLightingService, "/api/activity/lighting").setAllowedOrigins("*");
    }

}