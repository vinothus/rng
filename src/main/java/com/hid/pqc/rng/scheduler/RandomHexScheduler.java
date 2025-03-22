package com.hid.pqc.rng.scheduler;
import com.hid.pqc.rng.config.AppProperties;
import com.hid.pqc.rng.service.DynamoDbService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RandomHexScheduler {

    private final DynamoDbService dynamoDbService;
    private final AppProperties appProperties;

    public RandomHexScheduler(DynamoDbService dynamoDbService, AppProperties appProperties) {
        this.dynamoDbService = dynamoDbService;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedRateString = "#{@appProperties.scheduler.fixedRate}")
    public void checkAndGenerateRandomHex() {
        int requiredCount = appProperties.getRandom().getCount();
        int activeCount = dynamoDbService.getActiveRandomHexCount();

        if (activeCount < requiredCount) {
            int missingCount = requiredCount - activeCount;
            String jobID = java.util.UUID.randomUUID().toString(); // Generate a unique jobID
            dynamoDbService.queueRandomHexJobs(missingCount, jobID);
        }
    }
}