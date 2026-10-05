package com.motivhub.be.user.exception;

public class CurrentPasswordMismatchException extends RuntimeException {
    public CurrentPasswordMismatchException(String message) {
        super(message);
    }
}
