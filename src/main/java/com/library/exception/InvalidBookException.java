package com.library.exception;

/**
 * Thrown when book data fails domain validation rules.
 */
public class InvalidBookException extends LibraryException {

    public InvalidBookException(String message) {
        super(message);
    }
}
