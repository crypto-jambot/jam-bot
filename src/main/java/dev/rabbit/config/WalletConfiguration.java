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
    private static final SecretManagerUtils secretManagerUtils = SecretManagerUtils.of();
    @Getter(AccessLevel.NONE)
    private String secretKey;

    @Getter(AccessLevel.NONE)
    private String projectId;

    private Integer numberOfAccounts;

    public String getSeedPhrase() {
        return secretManagerUtils.accessSecretVersionString(projectId, secretKey);
    }
}
