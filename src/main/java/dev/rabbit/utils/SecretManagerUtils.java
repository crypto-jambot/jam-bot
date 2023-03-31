package dev.rabbit.utils;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import lombok.NoArgsConstructor;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

@NoArgsConstructor(staticName = "of")
public class SecretManagerUtils {
    private ByteString secret;
    private String version;

    public ByteString accessSecretVersion(String projectId, String secretId, String version) {
        if (!ObjectUtils.isEmpty(secret) && !ObjectUtils.isEmpty(this.version) && version.equals(this.version)) {
            return this.secret;
        }
        this.version = version;
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretId, version);
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            Checksum checksum = new CRC32C();
            byte[] data = response.getPayload().getData().toByteArray();
            checksum.update(data, 0, data.length);
            if (response.getPayload().getDataCrc32C() != checksum.getValue()) {
                throw new RuntimeException("Data corruption detected.");
            }
            this.secret = response.getPayload().getData();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this.secret;
    }


    public String accessSecretVersionString(String projectId, String secretId) {
        return accessSecretVersion(projectId, secretId, "latest").toStringUtf8();
    }

    public String accessSecretVersion(String projectId, String secretId) {
        return accessSecretVersion(projectId, secretId);
    }


}
