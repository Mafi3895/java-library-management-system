package com.library.model;

import com.library.exception.InvalidBookException;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.junit.jupiter.api.Assertions.*;

class BookTest {

    @Test
    void shouldCreateValidBookWithDefaultAvailableAndNotDeleted() {
        // Arrange & Act
        Book book = new Book(" 978-0134685991 ", " Effective Java ", " Joshua Bloch ", 2018);

        // Assert
        assertEquals("978-0134685991", book.getIsbn());
        assertEquals("Effective Java", book.getTitle());
        assertEquals("Joshua Bloch", book.getAuthor());
        assertEquals(2018, book.getPublicationYear());
        assertTrue(book.isAvailable());
        assertFalse(book.isDeleted());
    }

    @Test
    void shouldRejectNullOrBlankIsbn() {
        assertThrows(InvalidBookException.class, () -> new Book(null, "Clean Code", "Robert C. Martin", 2008));
        assertThrows(InvalidBookException.class, () -> new Book("   ", "Clean Code", "Robert C. Martin", 2008));
    }

    @Test
    void shouldRejectNullOrBlankTitle() {
        assertThrows(InvalidBookException.class, () -> new Book("978-0132350884", null, "Robert C. Martin", 2008));
        assertThrows(InvalidBookException.class, () -> new Book("978-0132350884", " ", "Robert C. Martin", 2008));
    }

    @Test
    void shouldRejectNullOrBlankAuthor() {
        assertThrows(InvalidBookException.class, () -> new Book("978-0132350884", "Clean Code", null, 2008));
        assertThrows(InvalidBookException.class, () -> new Book("978-0132350884", "Clean Code", "", 2008));
    }

    @Test
    void shouldRejectInvalidPublicationYear() {
        int futureYear = Year.now().getValue() + 1;
        assertThrows(InvalidBookException.class, () -> new Book("978-0132350884", "Clean Code", "Robert C. Martin", 0));
        assertThrows(InvalidBookException.class, () -> new Book("978-0132350884", "Clean Code", "Robert C. Martin", -100));
        assertThrows(InvalidBookException.class, () -> new Book("978-0132350884", "Clean Code", "Robert C. Martin", futureYear));
    }

    @Test
    void shouldUpdateDetailsAtomicallyWithoutChangingIsbn() {
        // Arrange
        Book book = new Book("978-0134685991", "Effective Java", "Joshua Bloch", 2018);

        // Act
        book.updateDetails("Effective Java 3rd Ed", "J. Bloch", 2019);

        // Assert
        assertEquals("978-0134685991", book.getIsbn());
        assertEquals("Effective Java 3rd Ed", book.getTitle());
        assertEquals("J. Bloch", book.getAuthor());
        assertEquals(2019, book.getPublicationYear());
    }

    @Test
    void shouldNotMutateBookWhenUpdateDetailsFailsValidation() {
        // Arrange
        Book book = new Book("978-0134685991", "Effective Java", "Joshua Bloch", 2018);

        // Act & Assert
        assertThrows(InvalidBookException.class, () -> book.updateDetails("Valid Title", "Valid Author", -1));
        assertEquals("Effective Java", book.getTitle());
        assertEquals("Joshua Bloch", book.getAuthor());
        assertEquals(2018, book.getPublicationYear());
    }

    @Test
    void shouldToggleAvailabilityAndSoftDeleteState() {
        // Arrange
        Book book = new Book("978-0134685991", "Effective Java", "Joshua Bloch", 2018);

        // Act & Assert
        book.markAsBorrowed();
        assertFalse(book.isAvailable());

        book.markAsReturned();
        assertTrue(book.isAvailable());

        book.markAsDeleted();
        assertTrue(book.isDeleted());
    }
}
