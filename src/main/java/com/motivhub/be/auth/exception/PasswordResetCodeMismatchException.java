package com.motivhub.be.auth.exception;

public class PasswordResetCodeMismatchException extends RuntimeException {
    public PasswordResetCodeMismatchException(String message) {
        super(message);
    }
}
