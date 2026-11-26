package com.library.repository;

import com.library.database.DatabaseManager;
import com.library.exception.DatabaseException;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.repository.impl.JdbcBookRepository;
import com.library.repository.impl.JdbcLoanRepository;
import com.library.repository.impl.JdbcMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JdbcLoanRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private BookRepository bookRepository;
    private MemberRepository memberRepository;
    private LoanRepository loanRepository;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("loan-repo-test.db");
        databaseManager = new DatabaseManager("jdbc:sqlite:" + dbFile.toAbsolutePath());
        databaseManager.initializeDatabase();
        bookRepository = new JdbcBookRepository(databaseManager);
        memberRepository = new JdbcMemberRepository(databaseManager);
        loanRepository = new JdbcLoanRepository(databaseManager);
    }

    @Test
    void shouldSaveFindAndUpdateLoanWhilePreservingHistory() {
        // Arrange
        bookRepository.save(new Book("ISBN-100", "Effective Java", "Joshua Bloch", 2018));
        Member member = memberRepository.save(new Member("Ali Rezaei"));
        LocalDate borrowDate = LocalDate.of(2025, 2, 1);
        LocalDate returnDate = LocalDate.of(2025, 2, 10);

        // Act: create active loan
        Loan savedLoan = loanRepository.save(new Loan("ISBN-100", member.getId(), borrowDate));

        // Assert active state
        assertNotNull(savedLoan.getId());
        assertTrue(loanRepository.hasActiveLoan("ISBN-100"));
        Optional<Loan> activeLoan = loanRepository.findActiveByBookIsbn("ISBN-100");
        assertTrue(activeLoan.isPresent());
        assertEquals(savedLoan.getId(), activeLoan.get().getId());

        // Act: return loan
        savedLoan.markReturned(returnDate);
        loanRepository.update(savedLoan);

        // Assert returned state and preserved history
        assertFalse(loanRepository.hasActiveLoan("ISBN-100"));
        assertTrue(loanRepository.findActiveByBookIsbn("ISBN-100").isEmpty());

        List<Loan> history = loanRepository.findByBookIsbn("ISBN-100");
        assertEquals(1, history.size());
        assertEquals(returnDate, history.get(0).getReturnDate());

        List<Loan> memberLoans = loanRepository.findByMemberId(member.getId());
        assertEquals(1, memberLoans.size());
    }

    @Test
    void shouldRejectSecondActiveLoanForSameBookAtDatabaseLevel() {
        // Arrange
        bookRepository.save(new Book("ISBN-200", "Clean Code", "Robert C. Martin", 2008));
        Member m1 = memberRepository.save(new Member("Member 1"));
        Member m2 = memberRepository.save(new Member("Member 2"));
        loanRepository.save(new Loan("ISBN-200", m1.getId(), LocalDate.of(2025, 3, 1)));

        // Act & Assert
        assertThrows(DatabaseException.class, () ->
                loanRepository.save(new Loan("ISBN-200", m2.getId(), LocalDate.of(2025, 3, 2))));
    }

    @Test
    void shouldRollbackAllRepositoryOperationsWhenTransactionFails() {
        // Arrange
        bookRepository.save(new Book("ISBN-300", "Refactoring", "Martin Fowler", 2018));
        Member member = memberRepository.save(new Member("Sara Ahmadi"));

        // Act: simulate a failure after creating a loan and updating book availability
        assertThrows(RuntimeException.class, () ->
                databaseManager.executeInTransactionVoid(() -> {
                    loanRepository.save(new Loan("ISBN-300", member.getId(), LocalDate.of(2025, 4, 1)));
                    bookRepository.updateAvailability("ISBN-300", false);
                    throw new RuntimeException("Simulated mid-transaction failure");
                }));

        // Assert: both operations were rolled back cleanly
        assertFalse(loanRepository.hasActiveLoan("ISBN-300"));
        assertTrue(loanRepository.findAll().isEmpty());
        Book book = bookRepository.findByIsbn("ISBN-300").orElseThrow();
        assertTrue(book.isAvailable());
    }
}
