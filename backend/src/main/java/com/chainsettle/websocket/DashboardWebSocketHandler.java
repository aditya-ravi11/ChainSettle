package com.chainsettle.websocket;

import java.util.List;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class DashboardWebSocketHandler {
    private final SimpMessagingTemplate messagingTemplate;

    public DashboardWebSocketHandler(final SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishTransaction(final Object payload) {
        messagingTemplate.convertAndSend("/topic/transactions", payload);
    }

    public void publishEvent(final String eventName, final Object payload) {
        messagingTemplate.convertAndSend("/topic/events", Map.of(
            "event", eventName,
            "data", payload
        ));
    }

    public void publishBalances(final List<?> balances) {
        messagingTemplate.convertAndSend("/topic/balances", balances);
    }
}

