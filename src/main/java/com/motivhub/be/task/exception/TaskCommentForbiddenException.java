package com.motivhub.be.task.exception;

public class TaskCommentForbiddenException extends RuntimeException {
    public TaskCommentForbiddenException(String message) {
        super(message);
    }
}
