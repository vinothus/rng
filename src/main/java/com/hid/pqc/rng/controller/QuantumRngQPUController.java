package com.hid.pqc.rng.controller;

import com.hid.pqc.rng.processor.QuantumMultipleJobProcessor;
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

    private final BraketClient braketClient;

    @Value("${quantum.qpu.deviceArn}")
    private String deviceArn;

    @Value("${quantum.s3}")
    private String s3;
    @Autowired
    S3Client s3Client;
    @Value("${quantum.s3Prefix}")
    private String s3Prefix;
    private int POOL= 20;
    private QuantumMultipleJobProcessor quantumMultipleJobProcessor;

     @Autowired
    public QuantumRngQPUController(@Autowired BraketClient braketClient, @Value("${quantum.s3}")   String s3, @Value("${quantum.s3Prefix}")   String s3Prefix , @Value("${quantum.qpu.deviceArn}")     String deviceArn,    @Autowired    S3Client s3Client){
        this.braketClient = braketClient;
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


}
