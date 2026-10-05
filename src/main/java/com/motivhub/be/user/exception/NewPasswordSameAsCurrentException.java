package com.motivhub.be.user.exception;

public class NewPasswordSameAsCurrentException extends RuntimeException {
    public NewPasswordSameAsCurrentException(String message) {
        super(message);
    }
}
