package dev.rabbit.pojo;

import java.util.Map;

public record Body(Message message) {
    public record Message(String messageId, String publishTime, String data, Map<String, String> attributes) {}
}