package com.motivhub.be.auth.exception;

public class TooManyVerificationRequestsException extends RuntimeException {
    public TooManyVerificationRequestsException(String message) {
        super(message);
    }
}
