package com.motivhub.be.workspace.exception;

public class WorkspaceMemberNotFoundException extends RuntimeException {
    public WorkspaceMemberNotFoundException(String message) {
        super(message);
    }
}
