package com.library.model;

import java.time.LocalDate;

/**
 * Read-only projection combining loan details with book title and borrower name for history views.
 */
public record LoanHistoryEntry(
        Long loanId,
        String bookIsbn,
        String bookTitle,
        boolean bookDeleted,
        Long memberId,
        String memberName,
        LocalDate borrowDate,
        LocalDate returnDate,
        LoanStatus status
) {
}
