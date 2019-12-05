package com.example.demo.configs;

import com.example.demo.services.WebSocketNotificationSenderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@EnableWebSocket
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    WebSocketNotificationSenderService senderService;

    @Autowired
    private WebSocketJWTValidator webSocketJWTValidator;


    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry
                                                  webSocketHandlerRegistry) {

        webSocketHandlerRegistry.addHandler(createHandler(),
                "/handler")
                .setHandshakeHandler(new AuthenticationHandshakeHandler(webSocketJWTValidator, "setPrincipal"));

    }

    @Bean
    public WebSocketHandler createHandler() {

        return new MyHandler(senderService);

    }
}