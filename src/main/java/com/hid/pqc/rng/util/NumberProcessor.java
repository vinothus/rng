package com.hid.pqc.rng.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NumberProcessor {

private NumberProcessor()
{}
    public static long getRandomNumber(List<List<Integer>> measurements) throws Exception {
        final long maxRange = 10_000_000_000L; // 10-digit number range: 0 to 9,999,999,999

        for (List<Integer> binaryString : measurements) {
            // Convert binary string to decimal
            long decimalNumber = binaryToDecimal(binaryString);

            // Validate if the number is within the desired range
            if (decimalNumber < maxRange) {
                return decimalNumber;
            }
        }

        throw new Exception("No valid 10-digit random number found in the measurements.");
    }

    public static List<String> getListOfRandomNumber(List<List<Integer>> measurements) throws Exception {
        final long maxRange = 10_000_000_000L; // 10-digit number range: 0 to 9,999,999,999
        List<String> result = new ArrayList<>();
        for (List<Integer> binaryString : measurements) {
            // Convert binary string to decimal
            String decimalNumber = binaryToDecimalString(binaryString);

            // Validate if the number is within the desired range
            if (decimalNumber !=null) {
                result.add(decimalNumber);
            }
        }

        return result;
    }
    public static List<String> convertStringToList(String inputString) {
        // Step 1: Remove the square brackets
        String cleanedString = inputString.replaceAll("[\\[\\]]", "");

        // Step 2: Split the string by commas and trim whitespace
        List<String> resultHexArray = Arrays.stream(cleanedString.split(","))
                .map(String::trim) // Trim whitespace around each element
                .collect(Collectors.toList());

        return resultHexArray;
    }
    public static long binaryToDecimal(List<Integer> binaryString) {
        StringBuilder binaryStringBuilder = new StringBuilder();
        for (int bit : binaryString) {
            binaryStringBuilder.append(bit);
        }
        return Long.parseLong(binaryStringBuilder.toString(), 2);
    }

    public static String binaryToDecimalString(List<Integer> binaryString) {
        StringBuilder binaryStringBuilder = new StringBuilder();

        // Append bits to form the binary string
        for (int bit : binaryString) {
            binaryStringBuilder.append(bit);
        }

        // Convert binary string to BigInteger
        BigInteger bigInteger = new BigInteger(binaryStringBuilder.toString(), 2);

        // Return the decimal string
        return bigInteger.toString();
    }

}
