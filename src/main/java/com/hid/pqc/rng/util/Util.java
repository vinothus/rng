package com.hid.pqc.rng.util;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Util {
    public static void loggMessage(Logger logger, String message, long startTime) {
        // Calculate end time and elapsed time
        long endTime = System.currentTimeMillis();
        long timeElapsed = endTime - startTime;
        double timeInSeconds = timeElapsed / 1000.0; // Convert to seconds

        // Format the start time and end time using SimpleDateFormat
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy:HH:mm:ss:SSS");
        String formattedStartTime = dateFormat.format(new Date(startTime));
        String formattedEndTime = dateFormat.format(new Date(endTime));

        // Log the message with both start and end times
        logger.info("Thread Name : {} == Start Time : {} == End Time : {} == Message : {} == Time taken to complete : {} sec",
                Thread.currentThread().getName(), formattedStartTime, formattedEndTime, message, timeInSeconds);
    }
    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    public static void main(String[] args) throws InterruptedException {
        long startTime = System.currentTimeMillis();

        // Simulate some work
        Thread.sleep(1500); // Sleep for 1.5 seconds

        // Log the message
        Util.loggMessage(logger, "process message", startTime);
    }
}
