package com.hid.pqc.rng.processor;
import java.security.SecureRandom;

public class RandomNumberGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();

    public static String generateRandomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        String characters = "0123456789abcdef";

        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(secureRandom.nextInt(characters.length())));
        }

        return sb.toString();
    }
}