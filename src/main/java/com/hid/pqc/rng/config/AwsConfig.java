package com.hid.pqc.rng.config;

import com.hid.pqc.rng.processor.QuantumJobProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.braket.BraketClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
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
    public DynamoDbClient dynamoDbClient() {
        // Replace with your AWS access key and secret key

        // Build and return the DynamoDbClient
        return DynamoDbClient.builder()
                .region(Region.US_EAST_1) // Replace with your desired region
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
    @Bean
        public QuantumJobProcessor quantumJobProcessor()
    {
        return new QuantumJobProcessor();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Apply to all endpoints
                        .allowedOrigins("*") // Allow all origins
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Allowed HTTP methods
                        .allowedHeaders("*"); // Allow all headers
            }
        };
    }
}
