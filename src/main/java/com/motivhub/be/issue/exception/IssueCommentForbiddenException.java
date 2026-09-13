package com.motivhub.be.issue.exception;

public class IssueCommentForbiddenException extends RuntimeException {
    public IssueCommentForbiddenException(String message) {
        super(message);
    }
}
