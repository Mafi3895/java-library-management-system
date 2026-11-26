package com.library.exception;

/**
 * Thrown when an unrecoverable database or JDBC infrastructure error occurs.
 */
public class DatabaseException extends LibraryException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
