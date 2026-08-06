package com.motivhub.be.user.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class NicknameValidator {

    private static final Pattern PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9]{2,15}$");

    public boolean isValidFormat(String nickname) {
        if (nickname == null) {
            return false;
        }
        return PATTERN.matcher(nickname).matches();
    }
}
