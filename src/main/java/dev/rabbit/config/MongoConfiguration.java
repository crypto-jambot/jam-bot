package dev.rabbit.config;

import dev.rabbit.utils.SecretManagerUtils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "mongo")
@Getter
@Setter
public class MongoConfiguration {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static final SecretManagerUtils privateKeySecretManagerUtils = SecretManagerUtils.of();

    private String user;
    @Getter(AccessLevel.NONE)
    private String password;
    @Getter(AccessLevel.NONE)
    private String projectId;
    @Getter(AccessLevel.NONE)
    private String mongoKey;
    @Getter(AccessLevel.NONE)
    private String url;

    public String getPassword() {
        return privateKeySecretManagerUtils.accessSecretVersionString(projectId, mongoKey);
    }

    public String getUrl() {
        return String.format(url, getPassword());
    }
}