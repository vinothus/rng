package com.hid.pqc.rng.service;
import com.hid.pqc.rng.config.AppProperties;
import com.hid.pqc.rng.processor.RandomNumberGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
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
        String tableName = appProperties.getRandom().getTableName();

        // Build the ScanRequest with a filter expression
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("jobStatus = :jobStatus AND isConsumed = :isConsumed")
                .expressionAttributeValues(Map.of(
                        ":jobStatus", AttributeValue.builder().s("COMPLETED").build(),
                        ":isConsumed", AttributeValue.builder().s("false").build()
                ))
                .build();

        // Perform the scan operation
        ScanResponse response = dynamoDbClient.scan(scanRequest);

        // Return the count of matching records
        return response.items().size();
    }
    // Queue jobs by creating placeholder records with jobID
    public void queueRandomHexJobs(  String brackekJobIds, int count) {
        String tableName = appProperties.getRandom().getTableName();
        for (int i = 0; i <count  ; i++) {

            String recordID = java.util.UUID.randomUUID().toString();
            Instant now = Instant.now();

            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            "id", AttributeValue.builder().s(recordID).build(),
                            "jobID", AttributeValue.builder().s(brackekJobIds).build(), // Add jobID
                            "randomHex", AttributeValue.builder().nul(true).build(), // Placeholder for randomHex
                            "jobStatus", AttributeValue.builder().s("QUEUED").build(),
                            "isConsumed", AttributeValue.builder().s("false").build(), // Default to false
                            "createdAt", AttributeValue.builder().s(now.toString()).build(),
                            "updatedAt", AttributeValue.builder().s(now.toString()).build()
                    ))
                    .build();

            dynamoDbClient.putItem(putItemRequest);
        }
    }


    /**
     * Creates a new scheduled job record in DynamoDB.
     *
     * @param jobType The type of the job being scheduled.
     * @param parameters Optional parameters for the job.
     * @return The unique job ID of the created record.
     */
    public String createScheduled( String jobID,String jobType, Map<String, String> parameters) {
        String tableName = appProperties.getScheduler().getTableName(); // Get the scheduler table name
        Instant now = Instant.now();

        // Build the item to insert into DynamoDB
        Map<String, AttributeValue> item = Map.of(
                "jobID", AttributeValue.builder().s(jobID).build(),
                "jobType", AttributeValue.builder().s(jobType).build(),
                "jobStatus", AttributeValue.builder().s("QUEUED").build(),
                "createdAt", AttributeValue.builder().s(now.toString()).build(),
                "updatedAt", AttributeValue.builder().s(now.toString()).build()
        );

        // Add optional parameters if provided
        if (parameters != null && !parameters.isEmpty()) {
            parameters.forEach((key, value) -> item.put(key, AttributeValue.builder().s(value).build()));
        }

        // Create the PutItemRequest
        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        // Insert the record into DynamoDB
        dynamoDbClient.putItem(putItemRequest);

        return jobID; // Return the unique job ID
    }

    public boolean isJobAlreadyScheduled() {
        String tableName = appProperties.getScheduler().getTableName();

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .indexName("jobStatus-index") // Name of the GSI
                .keyConditionExpression("jobStatus = :jobStatus")
                .expressionAttributeValues(Map.of(
                        ":jobStatus", AttributeValue.builder().s("QUEUED").build()
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
                .indexName("jobStatus-index")
                .keyConditionExpression("jobStatus = :jobStatus")
                .expressionAttributeValues(Map.of(
                        ":jobStatus", AttributeValue.builder().s("QUEUED").build()
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
                .updateExpression("SET #jobStatus = :jobStatus, resultHex = :resultHex, updatedAt = :updatedAt")
                .expressionAttributeNames(Map.of(
                        "#jobStatus", "jobStatus"
                ))
                .expressionAttributeValues(Map.of(
                        ":jobStatus", AttributeValue.builder().s("COMPLETED").build(),
                        ":resultHex", AttributeValue.builder().s(resultHex).build(),
                        ":updatedAt", AttributeValue.builder().s(now.toString()).build()
                ))
                .build();

        dynamoDbClient.updateItem(updateItemRequest);
    }

    /**
     * Updates multiple job records with corresponding resultHex values.
     *
     * @param jobID      The unique ID of the job.
     * @param resultHexArray An array of resultHex strings to update the records.
     */
    public void updateJobWithResult(String jobID, List<String> resultHexArray) {
        String tableName = appProperties.getRandom().getTableName();
        Instant now = Instant.now();

        // Step 1: Fetch all records with the given jobID
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .indexName("jobID-index")
                .keyConditionExpression("jobID = :jobID")
                .expressionAttributeValues(Map.of(
                        ":jobID", AttributeValue.builder().s(jobID).build()
                ))
                .build();

        QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
        List<Map<String, AttributeValue>> items = queryResponse.items();

        // Step 2: Validate the number of records matches the size of resultHexArray
        if (items.size() != resultHexArray.size()) {
            throw new IllegalArgumentException("Mismatch between fetched records count (" + items.size() +
                    ") and resultHex array size (" + resultHexArray.size() + ")");
        }
        try {
            // Step 3: Update each record with the corresponding resultHex value
            for (int i = 0; i < items.size(); i++) {
                Map<String, AttributeValue> item = items.get(i);
                String resultHex = resultHexArray.get(i);

                // Extract the primary key (jobID) from the fetched item
                String fetchedJobID = item.get("id").s();

                UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                        .tableName(tableName)
                        .key(Map.of(
                                "id", AttributeValue.builder().s(fetchedJobID).build(), // Partition key
                                "jobID", AttributeValue.builder().s(jobID).build() // Sort key
                        ))
                        .updateExpression("SET #jobStatus = :jobStatus, randomHex = :randomHex, updatedAt = :updatedAt")
                        .expressionAttributeNames(Map.of(
                                "#jobStatus", "jobStatus"
                        ))
                        .expressionAttributeValues(Map.of(
                                ":jobStatus", AttributeValue.builder().s("COMPLETED").build(),
                                ":randomHex", AttributeValue.builder().s(resultHex).build(),
                                ":updatedAt", AttributeValue.builder().s(now.toString()).build()
                        ))
                        .build();

                dynamoDbClient.updateItem(updateItemRequest);
            }
            updateScheduledJobStatus (jobID, "COMPLETED");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

        // Process queued jobs
    public void processQueuedJobs() {
        String tableName = appProperties.getRandom().getTableName();

        // Query for QUEUED jobs
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("jobStatus = :jobStatus")
                .expressionAttributeValues(Map.of(
                        ":jobStatus", AttributeValue.builder().s("QUEUED").build()
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
                .updateExpression("SET #jobStatus = :jobStatus, updatedAt = :updatedAt")
                .expressionAttributeNames(Map.of("#jobStatus", "jobStatus"))
                .expressionAttributeValues(Map.of(
                        ":jobStatus", AttributeValue.builder().s(status).build(),
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
                .updateExpression("SET #jobStatus = :jobStatus, updatedAt = :updatedAt")
                .expressionAttributeNames(Map.of("#jobStatus", "jobStatus"))
                .expressionAttributeValues(Map.of(
                        ":jobStatus", AttributeValue.builder().s(status).build(),
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
                        "jobStatus", AttributeValue.builder().s("QUEUED").build(),
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
                .updateExpression("SET randomHex = :randomHex, #jobStatus = :jobStatus, updatedAt = :updatedAt")
                .expressionAttributeNames(Map.of("#jobStatus", "jobStatus"))
                .expressionAttributeValues(Map.of(
                        ":randomHex", AttributeValue.builder().s(randomHex).build(),
                        ":jobStatus", AttributeValue.builder().s("COMPLETED").build(),
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
                        ":isConsumed", AttributeValue.builder().s("true").build(),
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
                .indexName("jobStatus-isConsumed-index")
                .keyConditionExpression("jobStatus = :jobStatus AND isConsumed = :isConsumed")
                .expressionAttributeValues(Map.of(
                        ":jobStatus", AttributeValue.builder().s("COMPLETED").build(),
                        ":isConsumed", AttributeValue.builder().s("false").build()
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
            String jobID  =  item.get("jobID").s();
            // Mark the record as consumed
            Instant now = Instant.now();
            UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                            "id", AttributeValue.builder().s(recordID).build(), // Partition key
                            "jobID", AttributeValue.builder().s(jobID).build()  // Sort key
                    )).updateExpression("SET isConsumed = :isConsumed, updatedAt = :updatedAt")
                    .expressionAttributeValues(Map.of(
                            ":isConsumed", AttributeValue.builder().s("true").build(),
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