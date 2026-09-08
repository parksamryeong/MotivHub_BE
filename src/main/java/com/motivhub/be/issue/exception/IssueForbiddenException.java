package com.motivhub.be.issue.exception;

public class IssueForbiddenException extends RuntimeException {
    public IssueForbiddenException(String message) {
        super(message);
    }
}
