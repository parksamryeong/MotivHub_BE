package com.motivhub.be.task.exception;

public class TaskEditForbiddenException extends RuntimeException {
    public TaskEditForbiddenException(String message) {
        super(message);
    }
}
