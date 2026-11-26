package com.library.exception;

/**
 * Thrown when a book cannot be deleted due to business rules (e.g., it currently has an active loan).
 */
public class BookDeletionException extends LibraryException {

    public BookDeletionException(String message) {
        super(message);
    }
}
