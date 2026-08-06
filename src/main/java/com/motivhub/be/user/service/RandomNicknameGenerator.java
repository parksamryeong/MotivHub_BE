package com.motivhub.be.user.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class RandomNicknameGenerator {

    private static final String PREFIX = "user";
    private static final String ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SUFFIX_LENGTH = 7;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        return PREFIX + suffix;
    }
}
