package com.hid.pqc.rng.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hid.pqc.rng.util.NumberProcessor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.braket.BraketClient;
import software.amazon.awssdk.services.braket.model.CreateQuantumTaskRequest;
import software.amazon.awssdk.services.braket.model.CreateQuantumTaskResponse;
import software.amazon.awssdk.services.braket.model.GetQuantumTaskRequest;
import software.amazon.awssdk.services.braket.model.GetQuantumTaskResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class QuantumMultipleJobProcessor {
    private final BraketClient braketClient;


    private  String deviceArn;


    private  String s3;

     S3Client  s3Client;

    private String s3Prefix;
    private String circuitFileName;
    private final ExecutorService executorService; // Configurable thread pool

    ObjectMapper objectMapper;

    public String getDeviceArn() {
        return deviceArn;
    }

    public void setDeviceArn(String deviceArn) {
        this. deviceArn = deviceArn;
    }

    public String getS3() {
        return s3;
    }
    public void setS3(String s3) {
        this.s3 =s3;
    }
    public S3Client getS3Client() {
        return s3Client;
    }

    public String getS3Prefix() {
        return s3Prefix;
    }

    public void setS3Prefix(String s3Prefix) {
        this.s3Prefix = s3Prefix;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String getCircuitFileName() {
        return circuitFileName;
    }

    public void setS3Client(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public void setCircuitFileName(String circuitFileName) {
        this.circuitFileName = circuitFileName;
    }

    public QuantumMultipleJobProcessor(BraketClient braketClient, String deviceArn, String s3, S3Client s3Client, String s3Prefix, int pool, String circuitFileName) {
        this.braketClient = braketClient;
        this.deviceArn = deviceArn;
        this.s3 = s3;
        this.s3Client = s3Client;
        this.s3Prefix = s3Prefix;
        this.executorService   = Executors.newFixedThreadPool(pool);
        this.circuitFileName=circuitFileName;
        objectMapper=new ObjectMapper();
    }
    public void processJobs(String requestId, int count,long shots) {
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> this.submitBraketJob(shots), executorService);futures.add(future);
        }

        // Wait for all tasks to complete and collect job ARNs
        List<String> jobArns = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        saveToS3(requestId, jobArns);
    }
    public void processJobs(String requestId, int count) {
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(this::submitBraketJob, executorService);
            futures.add(future);
        }

        // Wait for all tasks to complete and collect job ARNs
        List<String> jobArns = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        saveToS3(requestId, jobArns);
    }

    private String submitBraketJob() {
        // Define the task parameters
        CreateQuantumTaskRequest createQuantumTaskRequest = CreateQuantumTaskRequest.builder()
                .deviceArn(deviceArn) // Use the default simulator
                .outputS3Bucket(s3) // Replace with a valid S3 bucket
                .outputS3KeyPrefix(s3Prefix)
                .action(readQuantumTaskFromFile() )
                .shots(10L) // Number of shots to run the circuit
                .build();
        CreateQuantumTaskResponse createQuantumTaskResponse = braketClient.createQuantumTask(createQuantumTaskRequest);

        return createQuantumTaskResponse.quantumTaskArn();
    }

    private String submitBraketJob(long shots) {
        // Define the task parameters
        CreateQuantumTaskRequest createQuantumTaskRequest = CreateQuantumTaskRequest.builder()
                .deviceArn(deviceArn) // Use the default simulator
                .outputS3Bucket(s3) // Replace with a valid S3 bucket
                .outputS3KeyPrefix(s3Prefix)
                .action(readQuantumTaskFromFile() )
                .shots(shots) // Number of shots to run the circuit
                .build();
        CreateQuantumTaskResponse createQuantumTaskResponse = braketClient.createQuantumTask(createQuantumTaskRequest);

        return createQuantumTaskResponse.quantumTaskArn();
    }
    private void saveToS3(String requestId, List<String> jobArns) {
        String fileContent = String.join("\n", jobArns);
        String key = s3Prefix+"/"+requestId + "/QuantumJobs.txt";

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3)
                .key(key)
                .build();

        s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromString(fileContent, StandardCharsets.UTF_8));
    }
    private void saveResultToS3(String requestId, List<Map<String, Object>> jobResult) throws JsonProcessingException {

        String fileContent = objectMapper.writeValueAsString(jobResult);
        String key =s3Prefix+"/"+ requestId + "/QuantumJobsResult.txt";

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3)
                .key(key)
                .build();

        s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromString(fileContent, StandardCharsets.UTF_8));
    }
    private String readQuantumTaskFromFile()  {
        try {
            ClassPathResource resource = new ClassPathResource(circuitFileName);
            byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
    public ResponseEntity<List<Map<String, Object>>> getQuantumResults(String requestId,String s3Prefix) {
        this.s3Prefix = s3Prefix;
        return getQuantumResults(requestId);
    }


    @GetMapping("/results/{requestId}")
    public ResponseEntity<List<Map<String, Object>>> getQuantumResults(@PathVariable String requestId) {
        try {
            String fileKey =s3Prefix +"/"+requestId + "/QuantumJobs.txt";
            List<String> jobArns = readJobArnsFromS3(fileKey);
            List<Map<String, Object>> jobResults = new ArrayList<>();
            boolean allJobStatus = true;
            for (String jobArn : jobArns) {
                Map<String, Object> jobData = new HashMap<>();
                jobData.put("jobId", jobArn.replace("arn:aws:braket:us-east-1:160214462496:quantum-task/",""));

                // Fetch job status
                GetQuantumTaskRequest taskRequest = GetQuantumTaskRequest.builder()
                        .quantumTaskArn(jobArn)
                        .build();

                GetQuantumTaskResponse taskResponse = braketClient.getQuantumTask(taskRequest);
                String status = taskResponse.statusAsString();
                jobData.put("status", status);

                // If job is completed, fetch result.json and extract result
                if ("COMPLETED".equals(status)) {
                    String resultFileKey = s3Prefix+"/" + jobArn.split("/")[jobArn.split("/").length - 1] + "/results.json";
                    String resultJson = readS3File(resultFileKey);
                    String hexMeasurement = extractHexMeasurement(resultJson);
                    List<List<Integer>> measurementList = extractMeasurement(resultJson);

                    jobData.put("resultHex", hexMeasurement);
                    jobData.put("resultNumber", NumberProcessor.getListOfRandomNumber(measurementList));
                }else{
                    allJobStatus=false;
                }

                jobResults.add(jobData);
            }
            if(allJobStatus){
                saveResultToS3(requestId,jobResults);
            }
            return ResponseEntity.ok(jobResults);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of(Map.of("error", "Error retrieving results: " + e.getMessage())));
        }
    }

    public List<String> readJobArnsFromS3(String fileKey) {
        try {
            String content = readS3File(fileKey);
            return Arrays.asList(content.split("\n"));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String readS3File(String fileKey) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3)
                    .key(fileKey)
                    .build();

            ResponseInputStream<?> s3InputStream = s3Client.getObject(getObjectRequest);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s3InputStream));
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Error reading file: " + fileKey;
        }
    }
private List<List<Integer>> extractMeasurement(String jsonContent) throws JsonProcessingException {
    JsonNode rootNode = objectMapper.readTree(jsonContent);
    List<List<Integer>> result = new ArrayList<>();
    JsonNode measurements = rootNode.path("measurements");
    if(measurements==null&&measurements.size()!=0) {
        for (int i = 0; i < measurements.size(); i++) {
            JsonNode shotResult = measurements.get(i).path("shotResult");
            if (shotResult != null && (shotResult.toString().length() != 0)) {
                List<Integer> postSequence = objectMapper.convertValue(shotResult.path("postSequence"), List.class);
                result.add(postSequence);
            } else {
                break;
            }
        }
    }else{
        measurements = rootNode.path("measurementProbabilities");
        Iterator<String> fieldNames = measurements.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            //String value = measurements.get(key).toString();
            List<Integer> postSequence = key.chars()
                    .mapToObj(c -> Character.getNumericValue(c))
                    .collect(Collectors.toList());
            result.add(postSequence);
        }
    }
    if(result.isEmpty()) {
        return objectMapper.readValue(measurements.toString(), new TypeReference<>() {
        });

    }
     return result;
}
    private String extractHexMeasurement(String jsonContent) {
        try {
            List<String> hexadecimal = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            JsonNode measurements = rootNode.path("measurements");
            StringBuilder hexDecimal = new StringBuilder();
            if (measurements != null && measurements.size() != 0) {
            // Iterate through each shot
            for (int i = 0; i < measurements.size(); i++) {
                JsonNode shotResult = measurements.get(i).path("shotResult");

                // Extract preSequence and postSequence arrays
                if (shotResult != null && (shotResult.toString().length() != 0)) {
                    List<Integer> postSequence = objectMapper.convertValue(shotResult.path("postSequence"), List.class);

                    // Convert binary arrays to hexadecimal

                    String postHex = binaryListToHex(postSequence);
                    hexDecimal.append(postHex);
                    hexadecimal.add(postHex);
                } else {
                    break;
                }


            }
        }else{
                measurements = rootNode.path("measurementProbabilities");
                Iterator<String> fieldNames = measurements.fieldNames();
                ArrayList<String> bitstrings = new ArrayList<>();
                while (fieldNames.hasNext()) {
                    String key = fieldNames.next();
                    bitstrings.add(key);
                    String postHex = binaryToHex(key);
                    hexDecimal.append(postHex);
                    hexadecimal.add(postHex);
                }

            }
            if(hexDecimal.isEmpty()) {
                List<List<Integer>> result = objectMapper.readValue(measurements.toString(), new TypeReference<>() {
                });
                result.forEach(integers -> {
                    String postHex = binaryListToHex(integers);
                    hexadecimal.add(postHex);

                });
            }
            return hexadecimal.toString();
        } catch (Exception e) {
            return "Error extracting measurement";
        }
    }


    // Converts a binary string to a hexadecimal string
    public static String binaryToHex(String binaryStr) {
        // Pad to multiple of 4 bits (so each group of 4 maps to 1 hex digit)
        int length = binaryStr.length();
        int padding = (4 - (length % 4)) % 4;
        String paddedBinary = "0".repeat(padding) + binaryStr;

        // Convert binary to hex
        StringBuilder hexBuilder = new StringBuilder();
        for (int i = 0; i < paddedBinary.length(); i += 4) {
            String chunk = paddedBinary.substring(i, i + 4);
            int decimal = Integer.parseInt(chunk, 2);
            hexBuilder.append(Integer.toHexString(decimal));
        }

        return hexBuilder.toString();
    }

    // Helper method to convert a binary list to hexadecimal
    private static String binaryListToHex(List<Integer> binaryList) {
        // Convert the list of integers to a binary string
        StringBuilder binaryString = new StringBuilder();
        for (Integer bit : binaryList) {
            binaryString.append(bit);
        }

        // Pad the binary string to make its length a multiple of 4
        int paddingLength = (4 - binaryString.length() % 4) % 4;
        for (int i = 0; i < paddingLength; i++) {
            binaryString.insert(0, '0');
        }

        // Convert the binary string to a hexadecimal string
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < binaryString.length(); i += 4) {
            String chunk = binaryString.substring(i, i + 4);
            int decimal = Integer.parseInt(chunk, 2);
            hexString.append(String.format("%X", decimal));
        }

        return hexString.toString();
    }

}
