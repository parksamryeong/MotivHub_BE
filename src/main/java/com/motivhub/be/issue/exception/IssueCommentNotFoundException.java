package com.motivhub.be.issue.exception;

public class IssueCommentNotFoundException extends RuntimeException {
    public IssueCommentNotFoundException(String message) {
        super(message);
    }
}
