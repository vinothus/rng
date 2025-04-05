package com.hid.pqc.rng.controller;

import com.hid.pqc.rng.processor.QuantumMultipleJobProcessor;
import com.hid.pqc.rng.service.DynamoDbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.braket.BraketClient;
import software.amazon.awssdk.services.s3.S3Client;
import java.util.*;

@RestController
@RequestMapping("/api")
public class QuantumRngQPUController {

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
    private final DynamoDbService dynamoDbService;

     @Autowired
    public QuantumRngQPUController(@Autowired BraketClient braketClient, @Value("${quantum.s3}")   String s3, @Value("${quantum.s3Prefix}")   String s3Prefix , @Value("${quantum.qpu.deviceArn}")     String deviceArn, @Autowired    S3Client s3Client, DynamoDbService dynamoDbService){
        this.braketClient = braketClient;
         this.dynamoDbService = dynamoDbService;
         this.quantumMultipleJobProcessor = new QuantumMultipleJobProcessor(braketClient,deviceArn,s3,s3Client,s3Prefix,POOL,"QPU2.json");
    }
    @PostMapping("/qpu/run/jobs/{count}")
    public String runQuantumJobs(@PathVariable int count) {
        String requestId = UUID.randomUUID().toString();
        quantumMultipleJobProcessor.processJobs(requestId,count);

        return requestId; // Immediately return requestId
    }

    @GetMapping("/qpu/results/{requestId}")
    public ResponseEntity<List<Map<String, Object>>> getQuantumResults(@PathVariable String requestId) {
      return quantumMultipleJobProcessor.getQuantumResults(requestId);

    }

    /**
     * Endpoint to consume an unconsumed random hex number.
     *
     * @return ResponseEntity containing the consumed random hex number or an error message.
     */
    @GetMapping("/consume-random-number/{count}")
    public ResponseEntity<String> consumeRandomNumber(@PathVariable int count) {
        try {
            // Fetch and consume an unconsumed random hex number
            List<String> randomHexNumbers = dynamoDbService.consumeRandomHex(count);

            if (randomHexNumbers.isEmpty()) {
                return ResponseEntity.noContent().build(); // No unconsumed numbers available
            } else {
                return ResponseEntity.ok(randomHexNumbers.toString()); // No unconsumed numbers available
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error consuming random number: " + e.getMessage());
        }
    }



}
