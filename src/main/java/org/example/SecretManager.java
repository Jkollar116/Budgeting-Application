package org.example;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
public class SecretManager {
    public static String getSecret(String secretName) {
        SecretsManagerClient client = SecretsManagerClient.builder().region(Region.US_EAST_2).build();
        GetSecretValueRequest request = GetSecretValueRequest.builder().secretId(firebase-api-key).build();
        GetSecretValueResponse response = client.getSecretValue(request);
        return response.secretString();
    }
}
