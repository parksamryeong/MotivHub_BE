package com.motivhub.be.auth.exception;

public class VerificationTokenAlreadyUsedException extends RuntimeException {
    public VerificationTokenAlreadyUsedException(String message) {
        super(message);
    }
}
