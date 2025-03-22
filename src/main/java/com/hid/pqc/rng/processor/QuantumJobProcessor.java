package com.hid.pqc.rng.processor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.hid.pqc.rng.model.BraketProgram;
import com.hid.pqc.rng.util.NumberProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.braket.BraketClient;
import software.amazon.awssdk.services.braket.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
@Slf4j
public class QuantumJobProcessor {

    @Autowired
    S3Client s3Client;
    public  String getQuantumMeasurementResults(String jobArn,BraketClient braketClient) {

            // Fetch job details
           GetQuantumTaskResponse taskResponse = braketClient.getQuantumTask(GetQuantumTaskRequest.builder()
                .quantumTaskArn(jobArn)
                .build());

            // Check job status
        if (taskResponse.status() == QuantumTaskStatus.FAILED) {
            return "Job  Status: Failed with Reason " + taskResponse.failureReason();
        }
            if (taskResponse.status() != QuantumTaskStatus.COMPLETED) {
                return "Job  Status: " + taskResponse.status();
            }

            // Extract S3 result path
            String bucket = taskResponse.outputS3Bucket();
            String outPutDirectory = taskResponse.outputS3Directory();
           log.info("S3 Path: " + bucket);

            // Parse S3 bucket and key
            String s3Path = "s3://"+bucket+"/"+outPutDirectory;
            String key = extractKeyName(s3Path);

            // Fetch result file from S3
            return fetchResultFromS3(bucket, outPutDirectory+"/results.json");
        }


    private  String fetchResultFromS3(String bucket, String key)   {
        log.info(key);
        log.info(bucket);
        StringBuilder jsonContent = new StringBuilder();
        try {
            InputStream inputStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());

            // Read the contents of the JSON file
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line).append("\n");
            }

            // Print JSON data
            log.info("S3 File Content:");
            log.info(jsonContent.toString());
            reader.close();
        } catch (IOException e) {
            log.info(e.getMessage());
        }

        // Parse measurement data from JSON
            return parseMeasurementData(String.valueOf(jsonContent));

    }

    private static String parseMeasurementData(String jsonContent) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonContent);

            // Extract measurement results (Assuming "measurements" field exists in JSON)
            JsonNode measurementNode = rootNode.path("measurements");

            List<List<Integer>> result = objectMapper.readValue(measurementNode.toString(), new TypeReference<>() {});

            return Long.toString(getRandomNumber(result));
        } catch (Exception e) {
            return "Error parsing measurement data: " + e.getMessage();
        }
    }

    private static String extractBucketName(String s3Path) {
        return s3Path.replace("s3://", "").split("/")[0];
    }

    private static String extractKeyName(String s3Path) {
        return s3Path.replace("s3://", "").substring(s3Path.indexOf("/") + 1);
    }

    private static long getRandomNumber(List<List<Integer>> measurements) throws Exception {
        return NumberProcessor.getRandomNumber(measurements);

    }

    private static long binaryToDecimal(List<Integer> binaryString) {

        return NumberProcessor.binaryToDecimal(binaryString);
    }

    public String getQuantumRandomNumber(BraketProgram braketProgram, String deviceArn, String s3, String s3Prefix, BraketClient braketClient) throws InterruptedException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String quantumCircuit = objectMapper.writeValueAsString(braketProgram);



        // Define the task parameters
        CreateQuantumTaskRequest createQuantumTaskRequest = CreateQuantumTaskRequest.builder()
                .deviceArn(deviceArn) // Use the default simulator
                .outputS3Bucket(s3) // Replace with a valid S3 bucket
                .outputS3KeyPrefix(s3Prefix)
                .action(quantumCircuit)
                .shots(10L) // Number of shots to run the circuit
                .build();
        CreateQuantumTaskResponse createQuantumTaskResponse = braketClient.createQuantumTask(createQuantumTaskRequest);

        String taskId = createQuantumTaskResponse.quantumTaskArn();

        // Wait for the task to complete
        GetQuantumTaskResponse taskResponse;

        taskResponse = braketClient.getQuantumTask(GetQuantumTaskRequest.builder()
                .quantumTaskArn(taskId)
                .build());
        //Thread.sleep(5000); // Wait for 5 seconds before checking again
        String taskIdArn = createQuantumTaskResponse.quantumTaskArn();
        // Retrieve the results
        String resultId = taskResponse.responseMetadata().requestId();
        String resultS3Uri = taskResponse.outputS3Bucket();
        String directory = taskResponse.outputS3Directory();
        log.info(resultId);
        log.info(resultS3Uri);
        log.info(directory);
        log.info(taskResponse.jobArn());
        // Execute the task on AWS Braket
        //CreateQuantumTaskResponse response = braketClient.createQuantumTask(request);
        return taskIdArn; // Returns the task ARN (fetch results via another API)
    }


}
