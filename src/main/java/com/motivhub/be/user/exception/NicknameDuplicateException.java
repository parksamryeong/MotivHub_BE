package com.motivhub.be.user.exception;

public class NicknameDuplicateException extends RuntimeException {
    public NicknameDuplicateException(String message) {
        super(message);
    }
}
