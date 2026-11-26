package com.library.exception;

/**
 * Thrown when a requested book cannot be found or has been soft-deleted.
 */
public class BookNotFoundException extends LibraryException {

    public BookNotFoundException(String message) {
        super(message);
    }
}
