package com.motivhub.be.auth.exception;

public class VerificationCodeMismatchException extends RuntimeException {
    public VerificationCodeMismatchException(String message) {
        super(message);
    }
}
