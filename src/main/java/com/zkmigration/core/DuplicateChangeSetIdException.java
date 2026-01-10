package com.zkmigration.core;

public class DuplicateChangeSetIdException extends RuntimeException {
    public DuplicateChangeSetIdException(String message) {
        super(message);
    }
}
