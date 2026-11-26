package com.library.service;

import com.library.exception.BookAlreadyExistsException;
import com.library.exception.BookDeletionException;
import com.library.exception.BookNotFoundException;
import com.library.exception.DatabaseException;
import com.library.exception.InvalidBookException;
import com.library.model.Book;
import com.library.model.BookSearchCriteria;
import com.library.model.BookSortField;
import com.library.model.Loan;
import com.library.model.SortDirection;
import com.library.service.fake.InMemoryBookRepository;
import com.library.service.fake.InMemoryLoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookServiceTest {

    private InMemoryBookRepository bookRepository;
    private InMemoryLoanRepository loanRepository;
    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookRepository = new InMemoryBookRepository();
        loanRepository = new InMemoryLoanRepository();
        bookService = new BookService(bookRepository, loanRepository);
    }

    @Test
    void shouldRegisterNewBookSuccessfully() {
        // Act
        Book registered = bookService.registerBook("978-0134685991", "Effective Java", "Joshua Bloch", 2018);

        // Assert
        assertEquals("978-0134685991", registered.getIsbn());
        assertEquals("Effective Java", registered.getTitle());
        assertEquals("Joshua Bloch", registered.getAuthor());
        assertEquals(2018, registered.getPublicationYear());
        assertTrue(registered.isAvailable());
        assertFalse(registered.isDeleted());
        assertTrue(bookRepository.existsByIsbn("978-0134685991"));
    }

    @Test
    void shouldRejectDuplicateIsbnRegistration() {
        // Arrange
        bookService.registerBook("978-0134685991", "Effective Java", "Joshua Bloch", 2018);

        // Act & Assert
        assertThrows(BookAlreadyExistsException.class, () ->
                bookService.registerBook("978-0134685991", "Duplicate Book", "Other Author", 2020));
    }

    @Test
    void shouldRejectReusingIsbnOfSoftDeletedBookWithClearMessage() {
        // Arrange
        bookService.registerBook("ISBN-DELETED", "Old Edition", "Author", 2010);
        bookService.deleteBook("ISBN-DELETED");

        // Act & Assert
        BookAlreadyExistsException ex = assertThrows(BookAlreadyExistsException.class, () ->
                bookService.registerBook("ISBN-DELETED", "New Edition", "Author", 2022));
        assertTrue(ex.getMessage().contains("previously deleted"));
    }

    @Test
    void shouldTranslateConcurrentUniqueConstraintViolationOnRegister() {
        // Arrange: simulate concurrent insert violating SQLite books.isbn PRIMARY KEY
        InMemoryBookRepository concurrentConflictRepo = new InMemoryBookRepository() {
            @Override
            public void save(Book book) {
                throw new DatabaseException(
                        "Failed to insert book",
                        new RuntimeException("[SQLITE_CONSTRAINT_PRIMARYKEY] UNIQUE constraint failed: books.isbn")
                );
            }
        };
        BookService concurrentBookService = new BookService(concurrentConflictRepo, loanRepository);

        // Act & Assert
        assertThrows(BookAlreadyExistsException.class, () ->
                concurrentBookService.registerBook("ISBN-RACE", "Race Book", "Author", 2020));
    }

    @Test
    void shouldRejectInvalidBookDataOnRegistration() {
        int nextYear = Year.now().getValue() + 1;

        assertThrows(InvalidBookException.class, () ->
                bookService.registerBook(" ", "Effective Java", "Joshua Bloch", 2018));
        assertThrows(InvalidBookException.class, () ->
                bookService.registerBook("ISBN-1", "", "Joshua Bloch", 2018));
        assertThrows(InvalidBookException.class, () ->
                bookService.registerBook("ISBN-2", "Effective Java", null, 2018));
        assertThrows(InvalidBookException.class, () ->
                bookService.registerBook("ISBN-3", "Effective Java", "Joshua Bloch", nextYear));
    }

    @Test
    void shouldUpdateBookDetailsSuccessfully() {
        // Arrange
        bookService.registerBook("978-0132350884", "Clean Code", "Robert C. Martin", 2008);

        // Act
        Book updated = bookService.updateBook("978-0132350884", "Clean Code (2nd Ed)", "Uncle Bob", 2010);

        // Assert
        assertEquals("978-0132350884", updated.getIsbn());
        assertEquals("Clean Code (2nd Ed)", updated.getTitle());
        assertEquals("Uncle Bob", updated.getAuthor());
        assertEquals(2010, updated.getPublicationYear());

        Book reloaded = bookService.getBookByIsbn("978-0132350884");
        assertEquals("Clean Code (2nd Ed)", reloaded.getTitle());
    }

    @Test
    void shouldRejectIsbnModificationDuringUpdate() {
        // Arrange
        bookService.registerBook("ISBN-ORIGINAL", "Clean Code", "Robert C. Martin", 2008);

        // Act & Assert
        assertThrows(InvalidBookException.class, () ->
                bookService.updateBook("ISBN-ORIGINAL", "ISBN-MODIFIED", "Clean Code", "Robert C. Martin", 2008));
    }

    @Test
    void shouldRejectUpdateForNonExistentOrDeletedBook() {
        // Arrange
        bookService.registerBook("ISBN-DEL", "Old Book", "Author", 2000);
        bookService.deleteBook("ISBN-DEL");

        // Act & Assert
        assertThrows(BookNotFoundException.class, () ->
                bookService.updateBook("ISBN-UNKNOWN", "Title", "Author", 2020));
        assertThrows(BookNotFoundException.class, () ->
                bookService.updateBook("ISBN-DEL", "Title", "Author", 2020));
    }

    @Test
    void shouldSoftDeleteEligibleBookAndExcludeFromListingsAndSearches() {
        // Arrange
        bookService.registerBook("ISBN-1", "Effective Java", "Joshua Bloch", 2018);
        bookService.registerBook("ISBN-2", "Clean Code", "Robert C. Martin", 2008);

        // Act
        bookService.deleteBook("ISBN-1");

        // Assert
        List<Book> remaining = bookService.getAllBooks();
        assertEquals(1, remaining.size());
        assertEquals("ISBN-2", remaining.get(0).getIsbn());
        assertTrue(bookService.searchByTitle("Effective").isEmpty());
        assertThrows(BookNotFoundException.class, () -> bookService.getBookByIsbn("ISBN-1"));
    }

    @Test
    void shouldRejectDeletingBookWithActiveLoan() {
        // Arrange
        bookService.registerBook("ISBN-BORROWED", "Effective Java", "Joshua Bloch", 2018);
        bookRepository.updateAvailability("ISBN-BORROWED", false);
        loanRepository.save(new Loan("ISBN-BORROWED", 1L, LocalDate.of(2025, 1, 10)));

        // Act & Assert
        assertThrows(BookDeletionException.class, () -> bookService.deleteBook("ISBN-BORROWED"));
        assertFalse(bookRepository.findByIsbn("ISBN-BORROWED").orElseThrow().isDeleted());
    }

    @Test
    void shouldSearchFilterAndSortBooksCorrectly() {
        // Arrange
        bookService.registerBook("ISBN-1", "Clean Code", "Robert C. Martin", 2008);
        bookService.registerBook("ISBN-2", "Clean Architecture", "Robert C. Martin", 2017);
        bookService.registerBook("ISBN-3", "Clean Coder", "Robert C. Martin", 2017);
        bookRepository.updateAvailability("ISBN-3", false);
        bookService.registerBook("ISBN-4", "Effective Java", "Joshua Bloch", 2018);
        bookService.deleteBook("ISBN-4");

        // Act: multi-filter search (title + author + year + available)
        BookSearchCriteria criteria = new BookSearchCriteria("clean", "martin", 2017, true);
        List<Book> filtered = bookService.searchBooks(criteria, BookSortField.TITLE, SortDirection.ASC);

        // Assert
        assertEquals(1, filtered.size());
        assertEquals("ISBN-2", filtered.get(0).getIsbn());

        // Act: sort all active books by publication year DESC
        List<Book> sorted = bookService.getAllBooks(BookSortField.PUBLICATION_YEAR, SortDirection.DESC);
        assertEquals(3, sorted.size());
        assertEquals(2017, sorted.get(0).getPublicationYear());
        assertEquals(2008, sorted.get(2).getPublicationYear());
    }
}
