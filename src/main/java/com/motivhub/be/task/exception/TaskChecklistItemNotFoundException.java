package com.motivhub.be.task.exception;

public class TaskChecklistItemNotFoundException extends RuntimeException {
    public TaskChecklistItemNotFoundException(String message) {
        super(message);
    }
}
