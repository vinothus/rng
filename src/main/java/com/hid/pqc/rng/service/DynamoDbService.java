package com.hid.pqc.rng.service;
import com.hid.pqc.rng.config.AppProperties;
import com.hid.pqc.rng.processor.RandomNumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        String tableName = appProperties.getRandom().getTableName(); // Use table name from properties
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
    public void queueRandomHexJobs(  List<String> brackekJobIds) {
        String tableName = appProperties.getRandom().getTableName();
        for (String jobId : brackekJobIds) {

            String recordID = java.util.UUID.randomUUID().toString();
            Instant now = Instant.now();

            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            "id", AttributeValue.builder().s(recordID).build(),
                            "jobID", AttributeValue.builder().s(jobId).build(), // Add jobID
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

        public boolean isJobAlreadyScheduled() {
            String tableName = appProperties.getScheduler().getTableName(); // Use scheduler table name

            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("status = :status")
                    .expressionAttributeValues(Map.of(
                            ":status", AttributeValue.builder().s("QUEUED").build()
                    ))
                    .limit(1) // Limit to one result
                    .build();

            QueryResponse response = dynamoDbClient.query(queryRequest);

            return !response.items().isEmpty(); // Return true if any job is found
        }


    public String getRecentScheduledJobId() {
        String tableName = appProperties.getScheduler().getTableName(); // Use scheduler table name
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("status = :status")
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s("QUEUED").build()
                ))
                .scanIndexForward(false) // Sort in descending order (most recent first)
                .limit(1)
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        if (response.items().isEmpty()) {
            return null; // No jobs found
        }

        return response.items().get(0).get("jobID").s(); // Return the most recent job ID
    }

    /**
     * Updates a job record with the resultHex and marks it as COMPLETED.
     *
     * @param jobID     The unique ID of the job.
     * @param resultHex The result hex value to store.
     */
    public void updateJobWithResult(String jobID, String resultHex) {
        String tableName = appProperties.getScheduler().getTableName(); // Use scheduler table name
        Instant now = Instant.now();

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("jobID", AttributeValue.builder().s(jobID).build()))
                .updateExpression("SET #status = :status, resultHex = :resultHex, updatedAt = :updatedAt")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s("COMPLETED").build(),
                        ":resultHex", AttributeValue.builder().s(resultHex).build(),
                        ":updatedAt", AttributeValue.builder().s(now.toString()).build()
                ))
                .build();

        dynamoDbClient.updateItem(updateItemRequest);
    }
        // Process queued jobs
    public void processQueuedJobs() {
        String tableName = appProperties.getRandom().getTableName();

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
            updateJobWithRandomHex(recordID, randomHex);
        }
    }

    /**
     * Updates the status of a scheduled job.
     *
     * @param jobID  The unique ID of the job.
     * @param status The new status of the job.
     */
    public void updateScheduledJobStatus(String jobID, String status) {
        String tableName = appProperties.getScheduler().getTableName(); // Use scheduler table name
        Instant now = Instant.now();

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("jobID", AttributeValue.builder().s(jobID).build()))
                .updateExpression("SET #status = :status, updatedAt = :updatedAt")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s(status).build(),
                        ":updatedAt", AttributeValue.builder().s(now.toString()).build()
                ))
                .build();

        dynamoDbClient.updateItem(updateItemRequest);
    }
    // Update job status
    private void updateJobStatus(String recordID, String status) {
        String tableName = appProperties.getRandom().getTableName();
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
    /**
     * Creates a record for a new scheduled job.
     *
     * @param jobID The unique ID of the job.
     */
    public void createScheduledJobRecord(String jobID) {
        String tableName = appProperties.getScheduler().getTableName(); // Use scheduler table name
        Instant now = Instant.now();

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(tableName)
                .item(Map.of(
                        "jobID", AttributeValue.builder().s(jobID).build(),
                        "status", AttributeValue.builder().s("QUEUED").build(),
                        "createdAt", AttributeValue.builder().s(now.toString()).build(),
                        "updatedAt", AttributeValue.builder().s(now.toString()).build()
                ))
                .build();

        dynamoDbClient.putItem(putItemRequest);
    }

    // Update job with randomHex and mark as COMPLETED
    private void updateJobWithRandomHex(String recordID, String randomHex) {
        String tableName = appProperties.getRandom().getTableName();
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
        String tableName = appProperties.getRandom().getTableName();
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
    /**
     * Fetches and marks an unconsumed random hex number as consumed.
     *
     * @return The consumed random hex number or null if none are available.
     */
    public List<String> consumeRandomHex(int count) {
        String tableName = appProperties.getRandom().getTableName();
        List<String> consumedNumbers = new ArrayList<>();

        // Query for unconsumed random hex numbers
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("status = :status AND isConsumed = :isConsumed")
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s("COMPLETED").build(),
                        ":isConsumed", AttributeValue.builder().bool(false).build()
                ))
                .limit(count) // Limit to the requested count
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        if (response.items().isEmpty()) {
            return consumedNumbers; // Return empty list if no unconsumed numbers are available
        }

        // Process each unconsumed record
        for (Map<String, AttributeValue> item : response.items()) {
            String recordID = item.get("id").s();
            String randomHex = item.get("randomHex").s();

            // Mark the record as consumed
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

            // Add the consumed random hex number to the result list
            consumedNumbers.add(randomHex);
        }

        return consumedNumbers;
    }
}