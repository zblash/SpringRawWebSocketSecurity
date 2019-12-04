package com.example.demo.configs;

import com.example.demo.services.WebSocketNotificationSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

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
                .setHandshakeHandler(new AuthenticationHandshakeHandler(webSocketJWTValidator, "setPrincipal"))
                .addInterceptors(new HttpSessionHandshakeInterceptor()
                {
                    Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);
                    @Override
                    public void afterHandshake(ServerHttpRequest request,
                                               ServerHttpResponse response, WebSocketHandler wsHandler,
                                               @Nullable Exception ex) {


                    }

                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request,
                                                   ServerHttpResponse response, WebSocketHandler wsHandler,
                                                   Map<String, Object> attributes) throws Exception {
                        boolean b = super.beforeHandshake(request, response,
                                wsHandler, attributes);
                        return b;
                    }

                });
    }

    @Bean
    public WebSocketHandler createHandler() {

        return new MyHandler(senderService);

    }
}