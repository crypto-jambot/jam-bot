package dev.rabbit.gateway;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.messaging.Message;

@MessagingGateway(defaultRequestChannel = "inputMessageChannel")
public interface PubSubOutboundGateway {
    void sendToPubsub(Message<String> message);
}
