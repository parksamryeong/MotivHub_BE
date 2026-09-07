package com.motivhub.be.task.exception;

public class TaskCommentNotFoundException extends RuntimeException {
    public TaskCommentNotFoundException(String message) {
        super(message);
    }
}
