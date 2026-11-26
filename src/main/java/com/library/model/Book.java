package com.library.model;

import com.library.exception.InvalidBookException;

import java.time.Year;
import java.util.Objects;

/**
 * Domain entity representing a book in the library catalog.
 * ISBN is immutable once assigned.
 */
public class Book {

    public static final int MIN_PUBLICATION_YEAR = 1;

    private final String isbn;
    private String title;
    private String author;
    private int publicationYear;
    private boolean available;
    private boolean deleted;

    /**
     * Creates a newly registered book. New books are available and not deleted by default.
     */
    public Book(String isbn, String title, String author, int publicationYear) {
        this(isbn, title, author, publicationYear, true, false);
    }

    /**
     * Rehydrates a book with explicit availability and soft-deletion flags (e.g., from persistence).
     */
    public Book(String isbn, String title, String author, int publicationYear, boolean available, boolean deleted) {
        this.isbn = validateIsbn(isbn);
        this.title = validateTitle(title);
        this.author = validateAuthor(author);
        this.publicationYear = validatePublicationYear(publicationYear);
        this.available = available;
        this.deleted = deleted;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getPublicationYear() {
        return publicationYear;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Atomically validates and updates mutable book fields. ISBN cannot be modified.
     */
    public void updateDetails(String title, String author, int publicationYear) {
        String validTitle = validateTitle(title);
        String validAuthor = validateAuthor(author);
        int validYear = validatePublicationYear(publicationYear);

        this.title = validTitle;
        this.author = validAuthor;
        this.publicationYear = validYear;
    }

    public void markAsBorrowed() {
        this.available = false;
    }

    public void markAsReturned() {
        this.available = true;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void markAsDeleted() {
        this.deleted = true;
    }

    public static String validateIsbn(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new InvalidBookException("Book ISBN must not be null or empty.");
        }
        return isbn.trim();
    }

    public static String validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new InvalidBookException("Book title must not be null or empty.");
        }
        return title.trim();
    }

    public static String validateAuthor(String author) {
        if (author == null || author.trim().isEmpty()) {
            throw new InvalidBookException("Book author must not be null or empty.");
        }
        return author.trim();
    }

    public static int validatePublicationYear(int publicationYear) {
        int currentYear = Year.now().getValue();
        if (publicationYear < MIN_PUBLICATION_YEAR || publicationYear > currentYear) {
            throw new InvalidBookException(
                    "Publication year must be between " + MIN_PUBLICATION_YEAR + " and " + currentYear + "."
            );
        }
        return publicationYear;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Book book)) {
            return false;
        }
        return Objects.equals(isbn, book.isbn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isbn);
    }

    @Override
    public String toString() {
        return "Book{" +
                "isbn='" + isbn + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                ", publicationYear=" + publicationYear +
                ", available=" + available +
                ", deleted=" + deleted +
                '}';
    }
}
