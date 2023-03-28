package dev.rabbit.config;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.integration.outbound.PubSubMessageHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pubsub")
@Data
@Slf4j
public class PubsubConfiguration {
    private String topic;

    @Bean
    @ServiceActivator(inputChannel = "inputMessageChannel")
    public MessageHandler messageSender(PubSubTemplate pubSubTemplate) {
        PubSubMessageHandler adapter =
                new PubSubMessageHandler(pubSubTemplate, topic);
        adapter.setSuccessCallback((ackId, message) -> log.info("Message Sent."));
        adapter.setFailureCallback(
                (cause, message) -> {
                    if (!cause.equals("due to null")) {
                        log.info("Error");
                    }
                }
        );
        return adapter;
    }
}