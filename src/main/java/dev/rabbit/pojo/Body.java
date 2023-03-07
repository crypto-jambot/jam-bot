package dev.rabbit.pojo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class Body {
    private Message message;
    public record Message(String messageId, String publishTime, String data, Map<String, String> attributes) {}
}