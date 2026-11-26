package com.library.exception;

/**
 * Thrown when attempting to borrow a book that already has an active loan.
 */
public class BookAlreadyBorrowedException extends LibraryException {

    public BookAlreadyBorrowedException(String message) {
        super(message);
    }

    public BookAlreadyBorrowedException(String message, Throwable cause) {
        super(message, cause);
    }
}
