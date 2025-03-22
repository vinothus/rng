package com.hid.pqc.rng.service;
import com.hid.pqc.rng.config.AppProperties;
import com.hid.pqc.rng.processor.RandomNumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.Map;

@Service
public class DynamoDbService {

    private final DynamoDbClient dynamoDbClient;
    private final AppProperties appProperties;

    @Autowired
    public DynamoDbService(DynamoDbClient dynamoDbClient, AppProperties appProperties) {
        this.dynamoDbClient = dynamoDbClient;
        this.appProperties = appProperties;
    }

    // Get the count of active RandomHex records
    public int getActiveRandomHexCount() {
        String tableName = "RandomHexTable";
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("status = :status AND isConsumed = :isConsumed")
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s("COMPLETED").build(),
                        ":isConsumed", AttributeValue.builder().bool(false).build()
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);
        return response.count();
    }

    // Queue jobs by creating placeholder records with jobID
    public void queueRandomHexJobs(int count, String jobID) {
        String tableName = "RandomHexTable";

        for (int i = 0; i < count; i++) {
            String recordID = java.util.UUID.randomUUID().toString();
            Instant now = Instant.now();

            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            "id", AttributeValue.builder().s(recordID).build(),
                            "jobID", AttributeValue.builder().s(jobID).build(), // Add jobID
                            "randomHex", AttributeValue.builder().nul(true).build(), // Placeholder for randomHex
                            "status", AttributeValue.builder().s("QUEUED").build(),
                            "isConsumed", AttributeValue.builder().bool(false).build(), // Default to false
                            "createdAt", AttributeValue.builder().s(now.toString()).build(),
                            "updatedAt", AttributeValue.builder().s(now.toString()).build()
                    ))
                    .build();

            dynamoDbClient.putItem(putItemRequest);
        }
    }

    // Process queued jobs
    public void processQueuedJobs() {
        String tableName = "RandomHexTable";

        // Query for QUEUED jobs
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("status = :status")
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s("QUEUED").build()
                ))
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        for (Map<String, AttributeValue> item : response.items()) {
            String recordID = item.get("id").s();
            String jobID = item.get("jobID").s(); // Retrieve jobID
            updateJobStatus(recordID, "IN_PROGRESS");

            // Generate random hex and update the record
            String randomHex = RandomNumberGenerator.generateRandomHex(appProperties.getRandom().getLength());
            updateJobWithRandomHex(recordID, jobID, randomHex);
        }
    }

    // Update job status
    private void updateJobStatus(String recordID, String status) {
        String tableName = "RandomHexTable";
        Instant now = Instant.now();

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.builder().s(recordID).build()))
                .updateExpression("SET #status = :status, updatedAt = :updatedAt")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s(status).build(),
                        ":updatedAt", AttributeValue.builder().s(now.toString()).build()
                ))
                .build();

        dynamoDbClient.updateItem(updateItemRequest);
    }

    // Update job with randomHex and mark as COMPLETED
    private void updateJobWithRandomHex(String recordID, String jobID, String randomHex) {
        String tableName = "RandomHexTable";
        Instant now = Instant.now();

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.builder().s(recordID).build()))
                .updateExpression("SET randomHex = :randomHex, #status = :status, updatedAt = :updatedAt")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":randomHex", AttributeValue.builder().s(randomHex).build(),
                        ":status", AttributeValue.builder().s("COMPLETED").build(),
                        ":updatedAt", AttributeValue.builder().s(now.toString()).build()
                ))
                .build();

        dynamoDbClient.updateItem(updateItemRequest);
    }

    // Mark a random hex as consumed
    public void markAsConsumed(String recordID) {
        String tableName = "RandomHexTable";
        Instant now = Instant.now();

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.builder().s(recordID).build()))
                .updateExpression("SET isConsumed = :isConsumed, updatedAt = :updatedAt")
                .expressionAttributeValues(Map.of(
                        ":isConsumed", AttributeValue.builder().bool(true).build(),
                        ":updatedAt", AttributeValue.builder().s(now.toString()).build()
                ))
                .build();

        dynamoDbClient.updateItem(updateItemRequest);
    }
}