package com.motivhub.be.issue.exception;

public class IssueNotFoundException extends RuntimeException {
    public IssueNotFoundException(String message) {
        super(message);
    }
}
