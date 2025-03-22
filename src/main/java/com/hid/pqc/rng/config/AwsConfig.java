package com.hid.pqc.rng.config;

import com.hid.pqc.rng.processor.QuantumJobProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.braket.BraketClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsConfig {



    @Value("${aws.region}")
    private String region;

    @Bean
    public BraketClient braketClient() {


        return BraketClient.builder()
                .region(Region.of(region))  // ✅ Explicitly set the region
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
    @Bean
    public S3Client s3Client(){

    return S3Client.builder()
            .region(Region.US_EAST_1)  // Change to your S3 bucket region
            .credentialsProvider(DefaultCredentialsProvider.create())  // Uses AWS credentials from ~/.aws/credentials
            .build();
}

    @Bean
        public QuantumJobProcessor quantumJobProcessor()
    {
        return new QuantumJobProcessor();
    }

}
