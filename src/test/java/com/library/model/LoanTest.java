package com.library.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class LoanTest {

    @Test
    void shouldCreateActiveLoan() {
        // Arrange
        LocalDate borrowDate = LocalDate.of(2025, 1, 10);

        // Act
        Loan loan = new Loan("978-0134685991", 1L, borrowDate);

        // Assert
        assertNull(loan.getId());
        assertEquals("978-0134685991", loan.getBookIsbn());
        assertEquals(1L, loan.getMemberId());
        assertEquals(borrowDate, loan.getBorrowDate());
        assertNull(loan.getReturnDate());
        assertTrue(loan.isActive());
        assertEquals(LoanStatus.ACTIVE, loan.getStatus());
    }

    @Test
    void shouldMarkActiveLoanAsReturned() {
        // Arrange
        LocalDate borrowDate = LocalDate.of(2025, 1, 10);
        LocalDate returnDate = LocalDate.of(2025, 1, 20);
        Loan loan = new Loan(10L, "978-0134685991", 1L, borrowDate, null);

        // Act
        loan.markReturned(returnDate);

        // Assert
        assertFalse(loan.isActive());
        assertEquals(LoanStatus.RETURNED, loan.getStatus());
        assertEquals(returnDate, loan.getReturnDate());
    }

    @Test
    void shouldRejectReturnDateBeforeBorrowDate() {
        // Arrange
        LocalDate borrowDate = LocalDate.of(2025, 1, 10);
        LocalDate invalidReturnDate = LocalDate.of(2025, 1, 9);
        Loan loan = new Loan("978-0134685991", 1L, borrowDate);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> loan.markReturned(invalidReturnDate));
        assertThrows(IllegalArgumentException.class, () ->
                new Loan(1L, "978-0134685991", 1L, borrowDate, invalidReturnDate));
    }

    @Test
    void shouldRejectReturningAlreadyReturnedLoan() {
        // Arrange
        LocalDate borrowDate = LocalDate.of(2025, 1, 10);
        LocalDate returnDate = LocalDate.of(2025, 1, 15);
        Loan loan = new Loan(1L, "978-0134685991", 1L, borrowDate, returnDate);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> loan.markReturned(LocalDate.of(2025, 1, 16)));
    }

    @Test
    void shouldRejectInvalidConstructorArguments() {
        LocalDate today = LocalDate.now();
        assertThrows(IllegalArgumentException.class, () -> new Loan(" ", 1L, today));
        assertThrows(IllegalArgumentException.class, () -> new Loan("978-0134685991", null, today));
        assertThrows(IllegalArgumentException.class, () -> new Loan("978-0134685991", 0L, today));
        assertThrows(IllegalArgumentException.class, () -> new Loan("978-0134685991", 1L, null));
    }
}
