package com.motivhub.be.realtime.exception;

public class StompAuthenticationException extends RuntimeException {
    public StompAuthenticationException(String message) {
        super(message);
    }
}
