package com.library.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Domain entity representing a book borrowing transaction.
 * A loan is active while returnDate is null, and returned once returnDate is set.
 */
public class Loan {

    private Long id;
    private final String bookIsbn;
    private final Long memberId;
    private final LocalDate borrowDate;
    private LocalDate returnDate;

    /**
     * Creates a new active loan before persistence.
     */
    public Loan(String bookIsbn, Long memberId, LocalDate borrowDate) {
        this(null, bookIsbn, memberId, borrowDate, null);
    }

    /**
     * Rehydrates an existing loan record from persistence.
     */
    public Loan(Long id, String bookIsbn, Long memberId, LocalDate borrowDate, LocalDate returnDate) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("Loan ID must be a positive number.");
        }
        if (bookIsbn == null || bookIsbn.trim().isEmpty()) {
            throw new IllegalArgumentException("Book ISBN must not be null or empty.");
        }
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("Member ID must be a positive number.");
        }
        if (borrowDate == null) {
            throw new IllegalArgumentException("Borrow date must not be null.");
        }
        if (returnDate != null && returnDate.isBefore(borrowDate)) {
            throw new IllegalArgumentException("Return date cannot be before borrow date.");
        }

        this.id = id;
        this.bookIsbn = bookIsbn.trim();
        this.memberId = memberId;
        this.borrowDate = borrowDate;
        this.returnDate = returnDate;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Loan ID must be a positive number.");
        }
        this.id = id;
    }

    public String getBookIsbn() {
        return bookIsbn;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LocalDate getBorrowDate() {
        return borrowDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public boolean isActive() {
        return returnDate == null;
    }

    public LoanStatus getStatus() {
        return isActive() ? LoanStatus.ACTIVE : LoanStatus.RETURNED;
    }

    /**
     * Marks this loan as returned on the given date.
     */
    public void markReturned(LocalDate returnDate) {
        if (!isActive()) {
            throw new IllegalStateException("Loan is already marked as returned.");
        }
        if (returnDate == null) {
            throw new IllegalArgumentException("Return date must not be null.");
        }
        if (returnDate.isBefore(borrowDate)) {
            throw new IllegalArgumentException("Return date cannot be before borrow date.");
        }
        this.returnDate = returnDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Loan loan)) {
            return false;
        }
        if (id != null && loan.id != null) {
            return Objects.equals(id, loan.id);
        }
        return Objects.equals(id, loan.id)
                && Objects.equals(bookIsbn, loan.bookIsbn)
                && Objects.equals(memberId, loan.memberId)
                && Objects.equals(borrowDate, loan.borrowDate)
                && Objects.equals(returnDate, loan.returnDate);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(bookIsbn, memberId, borrowDate, returnDate);
    }

    @Override
    public String toString() {
        return "Loan{" +
                "id=" + id +
                ", bookIsbn='" + bookIsbn + '\'' +
                ", memberId=" + memberId +
                ", borrowDate=" + borrowDate +
                ", returnDate=" + returnDate +
                ", status=" + getStatus() +
                '}';
    }
}
