package com.motivhub.be.auth.exception;

public class TooManyPasswordResetAttemptsException extends RuntimeException {
    public TooManyPasswordResetAttemptsException(String message) {
        super(message);
    }
}
