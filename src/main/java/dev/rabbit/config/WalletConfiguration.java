package dev.rabbit.config;

import dev.rabbit.utils.SecretManagerUtils;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wallet")
public class WalletConfiguration {
    private static final SecretManagerUtils seedPhraseSecretManagerUtils = SecretManagerUtils.of();
    private static final SecretManagerUtils privateKeySecretManagerUtils = SecretManagerUtils.of();
    @Getter(AccessLevel.NONE)
    private String seedPhraseKey;

    @Getter(AccessLevel.NONE)
    private String flashBotKey;

    @Getter(AccessLevel.NONE)
    private String projectId;

    private Integer numberOfAccounts;

    public String getSeedPhrase() {
        return seedPhraseSecretManagerUtils.accessSecretVersionString(projectId, seedPhraseKey);
    }

    public String getPrivateKey() {
        return privateKeySecretManagerUtils.accessSecretVersionString(projectId, flashBotKey);
    }
}
