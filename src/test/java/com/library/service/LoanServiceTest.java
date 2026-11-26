package com.library.service;

import com.library.database.TransactionRunner;
import com.library.exception.BookAlreadyBorrowedException;
import com.library.exception.BookNotAvailableException;
import com.library.exception.BookNotFoundException;
import com.library.exception.LoanNotFoundException;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.LoanHistoryEntry;
import com.library.model.LoanStatus;
import com.library.service.fake.InMemoryBookRepository;
import com.library.service.fake.InMemoryLoanRepository;
import com.library.service.fake.InMemoryMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class LoanServiceTest {

    private InMemoryBookRepository bookRepository;
    private InMemoryMemberRepository memberRepository;
    private InMemoryLoanRepository loanRepository;
    private BookService bookService;
    private LoanService loanService;

    @BeforeEach
    void setUp() {
        bookRepository = new InMemoryBookRepository();
        memberRepository = new InMemoryMemberRepository();
        loanRepository = new InMemoryLoanRepository();
        TransactionRunner directTransactionRunner = new TransactionRunner() {
            @Override
            public <T> T executeInTransaction(Supplier<T> action) {
                return action.get();
            }
        };

        bookService = new BookService(bookRepository, loanRepository);
        loanService = new LoanService(bookRepository, memberRepository, loanRepository, directTransactionRunner);
    }

    @Test
    void shouldBorrowAvailableBookAndMarkUnavailable() {
        // Arrange
        bookService.registerBook("ISBN-1", "Effective Java", "Joshua Bloch", 2018);
        LocalDate borrowDate = LocalDate.of(2025, 3, 1);

        // Act
        Loan loan = loanService.borrowBook("ISBN-1", "Ali Rezaei", borrowDate);

        // Assert
        assertNotNull(loan.getId());
        assertEquals("ISBN-1", loan.getBookIsbn());
        assertEquals(borrowDate, loan.getBorrowDate());
        assertTrue(loan.isActive());
        assertEquals(LoanStatus.ACTIVE, loan.getStatus());

        Book book = bookRepository.findByIsbn("ISBN-1").orElseThrow();
        assertFalse(book.isAvailable());
        assertEquals(1, memberRepository.findAll().size());
    }

    @Test
    void shouldReuseExistingMemberOnSubsequentBorrow() {
        // Arrange
        bookService.registerBook("ISBN-1", "Effective Java", "Joshua Bloch", 2018);
        bookService.registerBook("ISBN-2", "Clean Code", "Robert C. Martin", 2008);

        // Act
        Loan firstLoan = loanService.borrowBook("ISBN-1", "Sara Ahmadi", LocalDate.of(2025, 3, 1));
        Loan secondLoan = loanService.borrowBook("ISBN-2", "sara ahmadi", LocalDate.of(2025, 3, 2));

        // Assert
        assertEquals(firstLoan.getMemberId(), secondLoan.getMemberId());
        assertEquals(1, memberRepository.findAll().size());
    }

    @Test
    void shouldRejectBorrowingAlreadyBorrowedBook() {
        // Arrange
        bookService.registerBook("ISBN-1", "Effective Java", "Joshua Bloch", 2018);
        loanService.borrowBook("ISBN-1", "Ali Rezaei", LocalDate.of(2025, 3, 1));

        // Act & Assert
        assertThrows(BookAlreadyBorrowedException.class, () ->
                loanService.borrowBook("ISBN-1", "Reza Karimi", LocalDate.of(2025, 3, 2)));
    }

    @Test
    void shouldRejectBorrowingUnavailableBook() {
        // Arrange
        bookRepository.save(new Book("ISBN-UNAVAIL", "Clean Code", "Robert C. Martin", 2008, false, false));

        // Act & Assert
        assertThrows(BookNotAvailableException.class, () ->
                loanService.borrowBook("ISBN-UNAVAIL", "Ali Rezaei", LocalDate.of(2025, 3, 1)));
    }

    @Test
    void shouldRejectBorrowingDeletedBook() {
        // Arrange
        bookService.registerBook("ISBN-DEL", "Old Book", "Author", 2000);
        bookService.deleteBook("ISBN-DEL");

        // Act & Assert
        assertThrows(BookNotAvailableException.class, () ->
                loanService.borrowBook("ISBN-DEL", "Ali Rezaei", LocalDate.of(2025, 3, 1)));
    }

    @Test
    void shouldRejectBorrowingNonExistentBook() {
        assertThrows(BookNotFoundException.class, () ->
                loanService.borrowBook("ISBN-MISSING", "Ali Rezaei", LocalDate.of(2025, 3, 1)));
    }

    @Test
    void shouldReturnBorrowedBookAndRestoreAvailability() {
        // Arrange
        bookService.registerBook("ISBN-1", "Effective Java", "Joshua Bloch", 2018);
        loanService.borrowBook("ISBN-1", "Ali Rezaei", LocalDate.of(2025, 3, 1));
        LocalDate returnDate = LocalDate.of(2025, 3, 10);

        // Act
        Loan returned = loanService.returnBook("ISBN-1", returnDate);

        // Assert
        assertFalse(returned.isActive());
        assertEquals(LoanStatus.RETURNED, returned.getStatus());
        assertEquals(returnDate, returned.getReturnDate());

        Book book = bookRepository.findByIsbn("ISBN-1").orElseThrow();
        assertTrue(book.isAvailable());
        assertTrue(loanService.getActiveLoanForBook("ISBN-1").isEmpty());
    }

    @Test
    void shouldRejectReturningBookWithoutActiveLoan() {
        // Arrange
        bookService.registerBook("ISBN-1", "Effective Java", "Joshua Bloch", 2018);

        // Act & Assert
        assertThrows(LoanNotFoundException.class, () ->
                loanService.returnBook("ISBN-1", LocalDate.of(2025, 3, 10)));
    }

    @Test
    void shouldPreserveBorrowingHistoryAcrossMultipleLoansAndAfterSoftDelete() {
        // Arrange
        bookService.registerBook("ISBN-1", "Effective Java", "Joshua Bloch", 2018);

        // Act: borrow and return twice, then soft-delete the book
        loanService.borrowBook("ISBN-1", "Ali Rezaei", LocalDate.of(2025, 1, 1));
        loanService.returnBook("ISBN-1", LocalDate.of(2025, 1, 10));

        loanService.borrowBook("ISBN-1", "Sara Ahmadi", LocalDate.of(2025, 2, 1));
        loanService.returnBook("ISBN-1", LocalDate.of(2025, 2, 15));

        bookService.deleteBook("ISBN-1");

        // Assert: full history remains intact and enriched
        List<LoanHistoryEntry> history = loanService.getBorrowingHistoryForBook("ISBN-1");
        assertEquals(2, history.size());
        assertEquals("Effective Java", history.get(0).bookTitle());
        assertTrue(history.get(0).bookDeleted());
        assertEquals(LoanStatus.RETURNED, history.get(0).status());
        assertEquals(LoanStatus.RETURNED, history.get(1).status());
    }
}
