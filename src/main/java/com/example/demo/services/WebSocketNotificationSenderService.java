package com.example.demo.services;

import com.example.demo.dtos.WrapperWsNotification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class WebSocketNotificationSenderService {

    private List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private ObjectMapper mapper = new ObjectMapper();

    public void addToSession(WebSocketSession session) {
        sessions.add(session);
    }

    public void convertAndSend(WrapperWsNotification payload) throws JsonProcessingException {
        String payloadString = mapper.writeValueAsString(payload);
        sessions.stream().forEach(session -> {
            try {
                session.sendMessage(new TextMessage(payloadString));
            } catch (IOException e) {
                e.printStackTrace();
            }

        });
    }

    public void convertAndSendToUser(String user, WrapperWsNotification payload) throws JsonProcessingException {
        String payloadString = mapper.writeValueAsString(payload);
        sessions.forEach(session -> {
            if (session.getPrincipal().getName().equals(user)) {
                try {
                    session.sendMessage(new TextMessage(payloadString));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
