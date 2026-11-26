package com.library.exception;

/**
 * Thrown when an expected loan (such as an active loan for a return operation) cannot be found.
 */
public class LoanNotFoundException extends LibraryException {

    public LoanNotFoundException(String message) {
        super(message);
    }
}
