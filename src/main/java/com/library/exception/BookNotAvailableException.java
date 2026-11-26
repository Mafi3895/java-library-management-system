package com.library.exception;

/**
 * Thrown when a book is not available for borrowing (e.g., marked unavailable or soft-deleted).
 */
public class BookNotAvailableException extends LibraryException {

    public BookNotAvailableException(String message) {
        super(message);
    }
}
