package com.motivhub.be.task.exception;

public class InvalidTaskStatusTransitionException extends RuntimeException {
    public InvalidTaskStatusTransitionException(String message) {
        super(message);
    }
}
