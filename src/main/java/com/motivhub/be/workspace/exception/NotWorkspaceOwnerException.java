package com.motivhub.be.workspace.exception;

public class NotWorkspaceOwnerException extends RuntimeException {
    public NotWorkspaceOwnerException(String message) {
        super(message);
    }
}
