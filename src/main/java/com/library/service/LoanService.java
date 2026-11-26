package com.library.service;

import com.library.database.TransactionRunner;
import com.library.exception.BookAlreadyBorrowedException;
import com.library.exception.BookNotAvailableException;
import com.library.exception.BookNotFoundException;
import com.library.exception.DatabaseException;
import com.library.exception.LoanNotFoundException;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.model.LoanHistoryEntry;
import com.library.model.Member;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import com.library.repository.MemberRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service layer responsible for borrowing, returning, member management, and borrowing history.
 * Multi-step state changes execute atomically inside transactions via {@link TransactionRunner}.
 */
public class LoanService {

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final TransactionRunner transactionRunner;

    public LoanService(
            BookRepository bookRepository,
            MemberRepository memberRepository,
            LoanRepository loanRepository,
            TransactionRunner transactionRunner
    ) {
        if (bookRepository == null) {
            throw new IllegalArgumentException("BookRepository must not be null.");
        }
        if (memberRepository == null) {
            throw new IllegalArgumentException("MemberRepository must not be null.");
        }
        if (loanRepository == null) {
            throw new IllegalArgumentException("LoanRepository must not be null.");
        }
        if (transactionRunner == null) {
            throw new IllegalArgumentException("TransactionRunner must not be null.");
        }
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.transactionRunner = transactionRunner;
    }

    /**
     * Borrows a book on today's date for the given borrower name.
     */
    public Loan borrowBook(String bookIsbn, String borrowerName) {
        return borrowBook(bookIsbn, borrowerName, LocalDate.now());
    }

    /**
     * Atomically borrows a book on the given date:
     * validates book existence/deletion/availability, finds or creates the member,
     * creates the loan record, and marks the book unavailable.
     */
    public Loan borrowBook(String bookIsbn, String borrowerName, LocalDate borrowDate) {
        String validIsbn = Book.validateIsbn(bookIsbn);
        String validBorrowerName = Member.validateName(borrowerName);
        LocalDate effectiveDate = borrowDate != null ? borrowDate : LocalDate.now();

        try {
            return transactionRunner.executeInTransaction(() -> {
                Book book = bookRepository.findByIsbn(validIsbn)
                        .orElseThrow(() -> new BookNotFoundException("Book not found with ISBN: " + validIsbn));

                if (book.isDeleted()) {
                    throw new BookNotAvailableException(
                            "Book with ISBN '" + validIsbn + "' has been deleted and cannot be borrowed."
                    );
                }

                if (loanRepository.hasActiveLoan(validIsbn)) {
                    throw new BookAlreadyBorrowedException(
                            "Book with ISBN '" + validIsbn + "' is already borrowed."
                    );
                }

                if (!book.isAvailable()) {
                    throw new BookNotAvailableException(
                            "Book with ISBN '" + validIsbn + "' is currently unavailable."
                    );
                }

                Member member = findOrCreateMember(validBorrowerName);
                Loan newLoan = new Loan(validIsbn, member.getId(), effectiveDate);
                Loan savedLoan = loanRepository.save(newLoan);
                bookRepository.updateAvailability(validIsbn, false);
                return savedLoan;
            });
        } catch (DatabaseException ex) {
            // Translate concurrent active-loan unique constraint violations into a domain exception
            if (isActiveLoanConstraintViolation(ex)) {
                throw new BookAlreadyBorrowedException(
                        "Book with ISBN '" + validIsbn + "' was concurrently borrowed by another request.",
                        ex
                );
            }
            throw ex;
        }
    }

    /**
     * Returns a currently borrowed book on today's date.
     */
    public Loan returnBook(String bookIsbn) {
        return returnBook(bookIsbn, LocalDate.now());
    }

    /**
     * Atomically returns a borrowed book:
     * verifies the book and its active loan, records the return date, and restores availability.
     */
    public Loan returnBook(String bookIsbn, LocalDate returnDate) {
        String validIsbn = Book.validateIsbn(bookIsbn);
        LocalDate effectiveReturnDate = returnDate != null ? returnDate : LocalDate.now();

        return transactionRunner.executeInTransaction(() -> {
            bookRepository.findByIsbn(validIsbn)
                    .orElseThrow(() -> new BookNotFoundException("Book not found with ISBN: " + validIsbn));

            Loan activeLoan = loanRepository.findActiveByBookIsbn(validIsbn)
                    .orElseThrow(() -> new LoanNotFoundException(
                            "No active loan found for book with ISBN: " + validIsbn
                    ));

            activeLoan.markReturned(effectiveReturnDate);
            loanRepository.update(activeLoan);
            bookRepository.updateAvailability(validIsbn, true);
            return activeLoan;
        });
    }

    /**
     * Returns all loan records (active and historical).
     */
    public List<Loan> getAllLoans() {
        return loanRepository.findAll();
    }

    /**
     * Returns all loan records for a specific book ISBN, including historical records of soft-deleted books.
     */
    public List<Loan> getLoanHistoryForBook(String bookIsbn) {
        String validIsbn = Book.validateIsbn(bookIsbn);
        return loanRepository.findByBookIsbn(validIsbn);
    }

    /**
     * Returns the currently active loan for a book, if any.
     */
    public Optional<Loan> getActiveLoanForBook(String bookIsbn) {
        String validIsbn = Book.validateIsbn(bookIsbn);
        return loanRepository.findActiveByBookIsbn(validIsbn);
    }

    /**
     * Returns enriched borrowing history entries combining loan, book, and member details.
     */
    public List<LoanHistoryEntry> getBorrowingHistory() {
        return enrichLoans(loanRepository.findAll());
    }

    /**
     * Returns enriched borrowing history entries for a specific book ISBN.
     */
    public List<LoanHistoryEntry> getBorrowingHistoryForBook(String bookIsbn) {
        String validIsbn = Book.validateIsbn(bookIsbn);
        return enrichLoans(loanRepository.findByBookIsbn(validIsbn));
    }

    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    private Member findOrCreateMember(String name) {
        List<Member> matchingMembers = memberRepository.findByName(name);
        if (!matchingMembers.isEmpty()) {
            return matchingMembers.get(0);
        }
        return memberRepository.save(new Member(name));
    }

    private List<LoanHistoryEntry> enrichLoans(List<Loan> loans) {
        Map<String, Optional<Book>> bookCache = new HashMap<>();
        Map<Long, Optional<Member>> memberCache = new HashMap<>();

        return loans.stream()
                .map(loan -> {
                    Optional<Book> book = bookCache.computeIfAbsent(loan.getBookIsbn(), bookRepository::findByIsbn);
                    Optional<Member> member = memberCache.computeIfAbsent(loan.getMemberId(), memberRepository::findById);

                    String bookTitle = book.map(Book::getTitle).orElse("<Unknown Book>");
                    boolean bookDeleted = book.map(Book::isDeleted).orElse(false);
                    String memberName = member.map(Member::getName).orElse("<Unknown Member>");

                    return new LoanHistoryEntry(
                            loan.getId(),
                            loan.getBookIsbn(),
                            bookTitle,
                            bookDeleted,
                            loan.getMemberId(),
                            memberName,
                            loan.getBorrowDate(),
                            loan.getReturnDate(),
                            loan.getStatus()
                    );
                })
                .toList();
    }

    private boolean isActiveLoanConstraintViolation(DatabaseException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.contains("UNIQUE constraint failed") && message.contains("loans.book_isbn")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
