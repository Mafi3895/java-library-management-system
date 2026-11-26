package com.library.repository;

import com.library.database.DatabaseManager;
import com.library.exception.DatabaseException;
import com.library.model.Book;
import com.library.model.BookSearchCriteria;
import com.library.model.BookSortField;
import com.library.model.SortDirection;
import com.library.repository.impl.JdbcBookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JdbcBookRepositoryTest {

    @TempDir
    Path tempDir;

    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("book-repo-test.db");
        DatabaseManager databaseManager = new DatabaseManager("jdbc:sqlite:" + dbFile.toAbsolutePath());
        databaseManager.initializeDatabase();
        bookRepository = new JdbcBookRepository(databaseManager);
    }

    @Test
    void shouldSaveAndFindBookByIsbn() {
        // Arrange
        Book book = new Book("978-0134685991", "Effective Java", "Joshua Bloch", 2018);

        // Act
        bookRepository.save(book);
        Optional<Book> found = bookRepository.findByIsbn("978-0134685991");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("Effective Java", found.get().getTitle());
        assertEquals("Joshua Bloch", found.get().getAuthor());
        assertEquals(2018, found.get().getPublicationYear());
        assertTrue(found.get().isAvailable());
        assertFalse(found.get().isDeleted());
        assertTrue(bookRepository.existsByIsbn("978-0134685991"));
    }

    @Test
    void shouldRejectDuplicateIsbnAtDatabaseLevel() {
        // Arrange
        bookRepository.save(new Book("978-0134685991", "Effective Java", "Joshua Bloch", 2018));

        // Act & Assert
        assertThrows(DatabaseException.class, () ->
                bookRepository.save(new Book("978-0134685991", "Another Title", "Another Author", 2020)));
    }

    @Test
    void shouldUpdateBookDetailsAndAvailability() {
        // Arrange
        Book book = new Book("978-0132350884", "Clean Code", "Robert C. Martin", 2008);
        bookRepository.save(book);

        // Act
        book.updateDetails("Clean Code: Revised", "Uncle Bob", 2009);
        bookRepository.update(book);
        bookRepository.updateAvailability("978-0132350884", false);

        // Assert
        Book updated = bookRepository.findByIsbn("978-0132350884").orElseThrow();
        assertEquals("Clean Code: Revised", updated.getTitle());
        assertEquals("Uncle Bob", updated.getAuthor());
        assertEquals(2009, updated.getPublicationYear());
        assertFalse(updated.isAvailable());
    }

    @Test
    void shouldSoftDeleteBookAndExcludeFromFindAllAndSearch() {
        // Arrange
        bookRepository.save(new Book("ISBN-1", "Java Concurrency in Practice", "Brian Goetz", 2006));
        bookRepository.save(new Book("ISBN-2", "Effective Java", "Joshua Bloch", 2018));

        // Act
        bookRepository.delete("ISBN-1");

        // Assert
        List<Book> activeBooks = bookRepository.findAll();
        assertEquals(1, activeBooks.size());
        assertEquals("ISBN-2", activeBooks.get(0).getIsbn());

        List<Book> searchResults = bookRepository.search(
                new BookSearchCriteria("Java", null, null, null),
                BookSortField.TITLE,
                SortDirection.ASC
        );
        assertEquals(1, searchResults.size());
        assertEquals("ISBN-2", searchResults.get(0).getIsbn());

        // findByIsbn still locates the soft-deleted record for integrity checks
        Book deleted = bookRepository.findByIsbn("ISBN-1").orElseThrow();
        assertTrue(deleted.isDeleted());
    }

    @Test
    void shouldFilterByMultipleCriteriaSimultaneously() {
        // Arrange
        bookRepository.save(new Book("ISBN-1", "Clean Code", "Robert C. Martin", 2008));
        bookRepository.save(new Book("ISBN-2", "Clean Architecture", "Robert C. Martin", 2017));
        Book borrowedBook = new Book("ISBN-3", "Clean Coder", "Robert C. Martin", 2017, false, false);
        bookRepository.save(borrowedBook);
        bookRepository.save(new Book("ISBN-4", "Effective Java", "Joshua Bloch", 2017));

        // Act
        BookSearchCriteria criteria = new BookSearchCriteria("clean", "martin", 2017, true);
        List<Book> results = bookRepository.search(criteria, BookSortField.TITLE, SortDirection.ASC);

        // Assert
        assertEquals(1, results.size());
        assertEquals("ISBN-2", results.get(0).getIsbn());
    }

    @Test
    void shouldSortBooksByTitleAuthorAndPublicationYear() {
        // Arrange
        bookRepository.save(new Book("ISBN-1", "Refactoring", "Martin Fowler", 2018));
        bookRepository.save(new Book("ISBN-2", "Clean Code", "Robert C. Martin", 2008));
        bookRepository.save(new Book("ISBN-3", "Domain-Driven Design", "Eric Evans", 2003));

        // Act & Assert: Title ASC
        List<Book> byTitleAsc = bookRepository.findAll(BookSortField.TITLE, SortDirection.ASC);
        assertEquals(List.of("ISBN-2", "ISBN-3", "ISBN-1"), byTitleAsc.stream().map(Book::getIsbn).toList());

        // Author DESC
        List<Book> byAuthorDesc = bookRepository.findAll(BookSortField.AUTHOR, SortDirection.DESC);
        assertEquals(List.of("ISBN-2", "ISBN-1", "ISBN-3"), byAuthorDesc.stream().map(Book::getIsbn).toList());

        // Publication year DESC
        List<Book> byYearDesc = bookRepository.findAll(BookSortField.PUBLICATION_YEAR, SortDirection.DESC);
        assertEquals(List.of("ISBN-1", "ISBN-2", "ISBN-3"), byYearDesc.stream().map(Book::getIsbn).toList());
    }

    @Test
    void shouldTreatPercentAndUnderscoreAsLiteralCharactersInSearch() {
        // Arrange
        bookRepository.save(new Book("ISBN-PCT", "100% Java Mastery", "Author_One", 2021));
        bookRepository.save(new Book("ISBN-PLAIN", "Java Basics", "Author Two", 2021));

        // Act: searching for literal '%' must not match 'Java Basics'
        List<Book> percentMatches = bookRepository.search(
                new BookSearchCriteria("%", null, null, null),
                BookSortField.TITLE,
                SortDirection.ASC
        );
        List<Book> underscoreMatches = bookRepository.search(
                new BookSearchCriteria(null, "_", null, null),
                BookSortField.TITLE,
                SortDirection.ASC
        );

        // Assert
        assertEquals(1, percentMatches.size());
        assertEquals("ISBN-PCT", percentMatches.get(0).getIsbn());
        assertEquals(1, underscoreMatches.size());
        assertEquals("ISBN-PCT", underscoreMatches.get(0).getIsbn());
    }
}
