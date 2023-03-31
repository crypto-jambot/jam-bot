package dev.rabbit.record;

import java.util.Map;

public record Body(PubSubMessage message) {
    public record PubSubMessage(String messageId, String publishTime, String data, Map<String, String> attributes) {
    }
}