package com.library.exception;

/**
 * Thrown when attempting to register a book with an ISBN that already exists.
 */
public class BookAlreadyExistsException extends LibraryException {

    public BookAlreadyExistsException(String message) {
        super(message);
    }

    public BookAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
