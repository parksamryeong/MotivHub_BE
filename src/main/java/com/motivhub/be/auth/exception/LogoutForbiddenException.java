package com.motivhub.be.auth.exception;

public class LogoutForbiddenException extends RuntimeException {
    public LogoutForbiddenException(String message) {
        super(message);
    }
}
