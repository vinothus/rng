package com.hid.pqc.rng.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hid.pqc.rng.model.BraketProgram;
import com.hid.pqc.rng.processor.QuantumJobProcessor;
import com.hid.pqc.rng.processor.QuantumMultipleJobProcessor;
import com.hid.pqc.rng.processor.RandomNumberGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.braket.BraketClient;
import software.amazon.awssdk.services.braket.model.*;
import software.amazon.awssdk.services.s3.S3Client;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Slf4j
public class RandomNumberController {
@Autowired
 QuantumJobProcessor quantumJobProcessor;

    private final BraketClient braketClient;

    @Value("${quantum.deviceArn}")
    private String deviceArn;

    @Value("${quantum.s3}")
    private String s3;
    @Autowired
    S3Client s3Client;
    @Value("${quantum.s3Prefix}")
    private String s3Prefix;

    @Value("${quantum.pool}")
    private int pool;
    private QuantumMultipleJobProcessor quantumMultipleJobProcessor;

    @Autowired
    public RandomNumberController(@Autowired BraketClient braketClient, @Value("${quantum.s3}")   String s3, @Value("${quantum.s3Prefix}")   String s3Prefix , @Value("${quantum.deviceArn}")     String deviceArn,    @Autowired    S3Client s3Client, @Value("${quantum.pool}") int pool){
        this.braketClient = braketClient;
        this.quantumMultipleJobProcessor = new QuantumMultipleJobProcessor(braketClient,deviceArn,s3,s3Client,s3Prefix,pool,"quantum2.json");
    }
    public RandomNumberController() {
        this.braketClient = BraketClient.create();
    }
    /* @PostMapping("/quantum/generate/rng")
   public  String  generateQuantumRandomNumbers(@RequestBody BraketProgram braketProgram) throws JsonProcessingException, InterruptedException {


       return quantumJobProcessor.getQuantumRandomNumber(braketProgram,deviceArn,s3,s3Prefix,braketClient);
   }

       @GetMapping("/quantum/job")
       public  String  generateQuantumRandomNumbersForJobArn(@RequestParam(name="jobArn") String jobArn) throws JsonProcessingException, InterruptedException {
   log.info(jobArn);

           return  quantumJobProcessor.getQuantumMeasurementResults(jobArn,braketClient);
       }
     @PostMapping("/sim/run/jobs/{count}")
       public String runQuantumJobs(@PathVariable(name = "count") int count) {
           String requestId = UUID.randomUUID().toString();
           quantumMultipleJobProcessor.processJobs(requestId,count);

           return requestId; // Immediately return requestId
       }*/
    @PostMapping("/hexadecimal/{count}")
    public List<String> runHexaDecimal(@PathVariable(name = "count") int count) {
        List<String> randomHexList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            randomHexList.add( RandomNumberGenerator.generateRandomHex(24));
        }
        return randomHexList;
    }
    @PostMapping("/hexadecimal/onebyOne/{count}")
    public List<String> runOneByOneHexaDecimal(@PathVariable(name = "count") int count) {
        List<String> randomHexList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            randomHexList.add( RandomNumberGenerator.generateRandomHexOneByOne(24));
        }
        return randomHexList;
    }

        @PostMapping("/IonQ/run/jobs/{count}/{shots}")
    public String runQuantumJobsWithShots(@PathVariable(name = "count") int count,@PathVariable(name = "shots") int shots) {
        if (count <= 0 || shots <= 0) {
            throw new IllegalArgumentException("Count and shots must be positive integers");
        }
        String requestId = UUID.randomUUID().toString();
        new QuantumMultipleJobProcessor(braketClient,"arn:aws:braket:us-east-1::device/qpu/ionq/Forte-1",s3,s3Client,s3Prefix,pool,"quantum2.json").processJobs(requestId,count,shots);

        return requestId; // Immediately return requestId
    }

    @PostMapping("/sim/run/jobs/{count}/{shots}")
    public String runQuantumJobsWithShotsSim(@PathVariable(name = "count") int count,@PathVariable(name = "shots") int shots) {
        if (count <= 0 || shots <= 0) {
            throw new IllegalArgumentException("Count and shots must be positive integers");
        }
        String requestId = UUID.randomUUID().toString();
        new QuantumMultipleJobProcessor(braketClient,"arn:aws:braket:::device/quantum-simulator/amazon/tn1",s3,s3Client,s3Prefix,pool,"quantum2.json").processJobs(requestId,count,shots);

        return requestId; // Immediately return requestId
    }


    @GetMapping("/sim/results/{requestId}")
    public ResponseEntity<List<Map<String, Object>>> getQuantumResults(@PathVariable(name="requestId") String requestId) {
        return quantumMultipleJobProcessor.getQuantumResults(requestId);

    }
    
}
