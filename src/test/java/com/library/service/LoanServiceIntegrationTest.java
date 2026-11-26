package com.library.service;

import com.library.database.DatabaseManager;
import com.library.exception.BookAlreadyBorrowedException;
import com.library.exception.BookDeletionException;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.LoanHistoryEntry;
import com.library.model.LoanStatus;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import com.library.repository.MemberRepository;
import com.library.repository.impl.JdbcBookRepository;
import com.library.repository.impl.JdbcLoanRepository;
import com.library.repository.impl.JdbcMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoanServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private BookRepository bookRepository;
    private MemberRepository memberRepository;
    private LoanRepository loanRepository;
    private BookService bookService;
    private LoanService loanService;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("loan-service-it.db");
        databaseManager = new DatabaseManager("jdbc:sqlite:" + dbFile.toAbsolutePath());
        databaseManager.initializeDatabase();

        bookRepository = new JdbcBookRepository(databaseManager);
        memberRepository = new JdbcMemberRepository(databaseManager);
        loanRepository = new JdbcLoanRepository(databaseManager);

        bookService = new BookService(bookRepository, loanRepository);
        loanService = new LoanService(bookRepository, memberRepository, loanRepository, databaseManager);
    }

    @Test
    void shouldExecuteFullBorrowReturnAndSoftDeleteWorkflowInSqlite() {
        // Arrange
        bookService.registerBook("978-0134685991", "Effective Java", "Joshua Bloch", 2018);

        // Act: borrow book
        Loan loan = loanService.borrowBook("978-0134685991", "Ali Rezaei", LocalDate.of(2025, 5, 1));

        // Assert: unavailable, cannot be borrowed again, cannot be deleted
        assertTrue(loan.isActive());
        assertFalse(bookService.getBookByIsbn("978-0134685991").isAvailable());
        assertThrows(BookAlreadyBorrowedException.class, () ->
                loanService.borrowBook("978-0134685991", "Sara Ahmadi", LocalDate.of(2025, 5, 2)));
        assertThrows(BookDeletionException.class, () -> bookService.deleteBook("978-0134685991"));

        // Act: return book
        Loan returned = loanService.returnBook("978-0134685991", LocalDate.of(2025, 5, 12));

        // Assert: available again and now eligible for soft delete
        assertEquals(LoanStatus.RETURNED, returned.getStatus());
        assertTrue(bookService.getBookByIsbn("978-0134685991").isAvailable());

        bookService.deleteBook("978-0134685991");
        assertTrue(bookService.getAllBooks().isEmpty());

        // Historical loan record remains accessible in SQLite
        List<LoanHistoryEntry> history = loanService.getBorrowingHistory();
        assertEquals(1, history.size());
        assertEquals("Effective Java", history.get(0).bookTitle());
        assertEquals("Ali Rezaei", history.get(0).memberName());
        assertTrue(history.get(0).bookDeleted());
    }

    @Test
    void shouldRollbackMemberAndLoanWhenBookAvailabilityUpdateFailsDuringBorrow() {
        // Arrange: register a book, then use a failing BookRepository wrapper that throws on updateAvailability
        bookService.registerBook("ISBN-TX-FAIL", "Clean Code", "Robert C. Martin", 2008);

        BookRepository failingBookRepo = new JdbcBookRepository(databaseManager) {
            @Override
            public void updateAvailability(String isbn, boolean available) {
                throw new RuntimeException("Simulated failure updating book availability");
            }
        };
        LoanService failingLoanService = new LoanService(
                failingBookRepo, memberRepository, loanRepository, databaseManager
        );

        // Act & Assert: borrow fails mid-transaction
        assertThrows(RuntimeException.class, () ->
                failingLoanService.borrowBook("ISBN-TX-FAIL", "Rollback User", LocalDate.of(2025, 6, 1)));

        // Verify complete rollback in SQLite: no loan, no member created, book still available
        assertTrue(loanRepository.findAll().isEmpty());
        assertTrue(memberRepository.findAll().isEmpty());
        Book book = bookRepository.findByIsbn("ISBN-TX-FAIL").orElseThrow();
        assertTrue(book.isAvailable());
    }
}
