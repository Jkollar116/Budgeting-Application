package org.example;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.PutObjectRequest;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Service for interacting with AWS S3 storage
 */
public class AwsS3Service {
    private AmazonS3 s3Client;
    private String bucketName;

    public AwsS3Service() {
        initialize();
    }

    private void initialize() {
        try {
            String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
            String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
            String region = System.getenv("AWS_REGION");
            this.bucketName = System.getenv("AWS_S3_BUCKET");

            if (accessKey == null || secretKey == null || region == null || bucketName == null) {
                System.err.println("AWS credentials or configuration missing. S3 operations will fail.");
                return;
            }

            BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
            this.s3Client = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withRegion(Regions.fromName(region))
                    .build();

            System.out.println("AWS S3 service initialized successfully.");
        } catch (Exception e) {
            System.err.println("Failed to initialize AWS S3 service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Stores a JSON object in S3
     */
    public String storeObject(String userId, String objectType, JSONObject data) throws IOException {
        if (s3Client == null) {
            throw new IOException("S3 client not initialized");
        }

        String objectId = UUID.randomUUID().toString();
        String key = String.format("users/%s/%s/%s.json", userId, objectType, objectId);

        byte[] contentBytes = data.toString().getBytes(StandardCharsets.UTF_8);
        InputStream contentStream = new ByteArrayInputStream(contentBytes);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentBytes.length);
        metadata.setContentType("application/json");

        PutObjectRequest putRequest = new PutObjectRequest(bucketName, key, contentStream, metadata);
        s3Client.putObject(putRequest);

        return objectId;
    }

    /**
     * Retrieves a JSON object from S3
     */
    public JSONObject getObject(String userId, String objectType, String objectId) throws IOException {
        if (s3Client == null) {
            throw new IOException("S3 client not initialized");
        }

        String key = String.format("users/%s/%s/%s.json", userId, objectType, objectId);

        try {
            S3Object object = s3Client.getObject(bucketName, key);
            S3ObjectInputStream stream = object.getObjectContent();

            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = stream.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }

            stream.close();
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            throw new IOException("Failed to retrieve object from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing object in S3
     */
    public void updateObject(String userId, String objectType, String objectId, JSONObject data) throws IOException {
        if (s3Client == null) {
            throw new IOException("S3 client not initialized");
        }

        String key = String.format("users/%s/%s/%s.json", userId, objectType, objectId);

        byte[] contentBytes = data.toString().getBytes(StandardCharsets.UTF_8);
        InputStream contentStream = new ByteArrayInputStream(contentBytes);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentBytes.length);
        metadata.setContentType("application/json");

        PutObjectRequest putRequest = new PutObjectRequest(bucketName, key, contentStream, metadata);
        s3Client.putObject(putRequest);
    }

    /**
     * Deletes an object from S3
     */
    public void deleteObject(String userId, String objectType, String objectId) {
        if (s3Client == null) {
            return;
        }

        String key = String.format("users/%s/%s/%s.json", userId, objectType, objectId);
        s3Client.deleteObject(bucketName, key);
    }

    /**
     * Lists all objects of a specific type for a user
     */
    public JSONObject listObjects(String userId, String objectType) throws IOException {
        if (s3Client == null) {
            throw new IOException("S3 client not initialized");
        }

        String prefix = String.format("users/%s/%s/", userId, objectType);

        try {
            JSONObject result = new JSONObject();
            s3Client.listObjects(bucketName, prefix).getObjectSummaries().forEach(summary -> {
                String key = summary.getKey();
                String id = key.substring(key.lastIndexOf('/') + 1, key.lastIndexOf('.'));
                result.put(id, summary.getLastModified().toString());
            });

            return result;
        } catch (Exception e) {
            throw new IOException("Failed to list objects in S3: " + e.getMessage(), e);
        }
    }
}
