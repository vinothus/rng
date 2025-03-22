package com.hid.pqc.rng.scheduler;
import com.hid.pqc.rng.config.AppProperties;
import com.hid.pqc.rng.processor.QuantumMultipleJobProcessor;
import com.hid.pqc.rng.service.DynamoDbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.braket.BraketClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.Map;

@Component
public class RandomHexScheduler {

    private final DynamoDbService dynamoDbService;
    private final AppProperties appProperties;
     final BraketClient braketClient;

    @Value("${quantum.qpu.deviceArn}")
    private String deviceArn;

    @Value("${quantum.s3}")
    private String s3;
    @Autowired
    S3Client s3Client;
    @Value("${quantum.s3Prefix}")
    private String s3Prefix;
    public static int POOL= 20;
    private QuantumMultipleJobProcessor quantumMultipleJobProcessor;

    public RandomHexScheduler(DynamoDbService dynamoDbService, AppProperties appProperties, BraketClient braketClient) {
        this.dynamoDbService = dynamoDbService;
        this.appProperties = appProperties;
        this.braketClient = braketClient;
        this.quantumMultipleJobProcessor = new QuantumMultipleJobProcessor(braketClient,deviceArn,s3,s3Client,s3Prefix,POOL,"QPU2.json");

    }

    @Scheduled(fixedRateString = "#{@appProperties.scheduler.fixedRate}")
    public void checkAndGenerateRandomHex() {
        int requiredCount = appProperties.getRandom().getCount();
        int activeCount = dynamoDbService.getActiveRandomHexCount();

        if (activeCount < requiredCount) {
            boolean isJobAlreadyScheduled = dynamoDbService.isJobAlreadyScheduled();

            if (!isJobAlreadyScheduled) {
                // Generate a unique jobID and queue the job
                String jobID = java.util.UUID.randomUUID().toString();
                int missingCount = requiredCount - activeCount;
                quantumMultipleJobProcessor.processJobs(jobID,1,missingCount);
                String fileKey =s3Prefix +"/"+jobID + "/QuantumJobs.txt";
                List<String> jobArns = quantumMultipleJobProcessor.readJobArnsFromS3(fileKey);

                // Mark the job as QUEUED in DynamoDB
                dynamoDbService.createScheduledJobRecord(jobID);
                // Queue the job to generate random hex numbers
                dynamoDbService.queueRandomHexJobs(jobArns);
            }else{
                String jobID =  dynamoDbService.getRecentScheduledJobId();
                List<Map<String, Object>>  result =  quantumMultipleJobProcessor.getQuantumResults(jobID).getBody();
                if (result != null) {
                    // Loop through the results and update DynamoDB
                    for (Map<String, Object> resultMap : result) {
                        String status = (String) resultMap.get("status");
                        String resultHex = (String) resultMap.get("resultHex");

                        if ("COMPLETED".equals(status)) {
                            // Update DynamoDB with the completed status and resultHex
                            dynamoDbService.updateJobWithResult(jobID, resultHex);
                        }
                    }
                }
            }
        }
    }
}