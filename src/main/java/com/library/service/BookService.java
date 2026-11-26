package com.library.service;

import com.library.exception.BookAlreadyExistsException;
import com.library.exception.BookDeletionException;
import com.library.exception.BookNotFoundException;
import com.library.exception.InvalidBookException;
import com.library.model.Book;
import com.library.model.BookSearchCriteria;
import com.library.model.BookSortField;
import com.library.model.SortDirection;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;

import java.util.List;

/**
 * Service layer responsible for book catalog business rules and operations.
 */
public class BookService {

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;

    public BookService(BookRepository bookRepository, LoanRepository loanRepository) {
        if (bookRepository == null) {
            throw new IllegalArgumentException("BookRepository must not be null.");
        }
        if (loanRepository == null) {
            throw new IllegalArgumentException("LoanRepository must not be null.");
        }
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
    }

    /**
     * Registers a new book after validating all fields and ISBN uniqueness.
     */
    public Book registerBook(String isbn, String title, String author, int publicationYear) {
        Book book = new Book(isbn, title, author, publicationYear);

        if (bookRepository.existsByIsbn(book.getIsbn())) {
            throw new BookAlreadyExistsException(
                    "A book with ISBN '" + book.getIsbn() + "' is already registered."
            );
        }

        bookRepository.save(book);
        return book;
    }

    /**
     * Returns all non-deleted books in default sort order (title ascending).
     */
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    /**
     * Returns all non-deleted books sorted by the given field and direction.
     */
    public List<Book> getAllBooks(BookSortField sortField, SortDirection sortDirection) {
        return bookRepository.findAll(sortField, sortDirection);
    }

    /**
     * Finds an active (non-deleted) book by ISBN.
     */
    public Book getBookByIsbn(String isbn) {
        String validIsbn = Book.validateIsbn(isbn);
        Book book = bookRepository.findByIsbn(validIsbn)
                .orElseThrow(() -> new BookNotFoundException("Book not found with ISBN: " + validIsbn));

        if (book.isDeleted()) {
            throw new BookNotFoundException("Book with ISBN '" + validIsbn + "' has been deleted.");
        }
        return book;
    }

    /**
     * Searches non-deleted books by title substring (case-insensitive).
     */
    public List<Book> searchByTitle(String title) {
        BookSearchCriteria criteria = new BookSearchCriteria(title, null, null, null);
        return bookRepository.search(criteria, BookSortField.TITLE, SortDirection.ASC);
    }

    /**
     * Searches non-deleted books using multiple simultaneous filters and optional sorting.
     */
    public List<Book> searchBooks(BookSearchCriteria criteria, BookSortField sortField, SortDirection sortDirection) {
        BookSearchCriteria effectiveCriteria = criteria != null ? criteria : BookSearchCriteria.empty();
        return bookRepository.search(effectiveCriteria, sortField, sortDirection);
    }

    /**
     * Updates mutable book information (title, author, publication year). ISBN remains immutable.
     */
    public Book updateBook(String isbn, String newTitle, String newAuthor, int newPublicationYear) {
        Book existing = getBookByIsbn(isbn);
        existing.updateDetails(newTitle, newAuthor, newPublicationYear);
        bookRepository.update(existing);
        return existing;
    }

    /**
     * Updates mutable book information while explicitly rejecting any attempt to modify the ISBN.
     */
    public Book updateBook(
            String currentIsbn,
            String requestedIsbn,
            String newTitle,
            String newAuthor,
            int newPublicationYear
    ) {
        String validCurrentIsbn = Book.validateIsbn(currentIsbn);
        String validRequestedIsbn = Book.validateIsbn(requestedIsbn);

        if (!validCurrentIsbn.equals(validRequestedIsbn)) {
            throw new InvalidBookException(
                    "ISBN is immutable and cannot be changed from '" + validCurrentIsbn + "' to '" + validRequestedIsbn + "'."
            );
        }
        return updateBook(validCurrentIsbn, newTitle, newAuthor, newPublicationYear);
    }

    /**
     * Soft-deletes a book only if it exists, is not already deleted, and has no active loan.
     */
    public void deleteBook(String isbn) {
        Book existing = getBookByIsbn(isbn);

        if (loanRepository.hasActiveLoan(existing.getIsbn()) || !existing.isAvailable()) {
            throw new BookDeletionException(
                    "Cannot delete book with ISBN '" + existing.getIsbn() + "' because it is currently borrowed."
            );
        }

        bookRepository.delete(existing.getIsbn());
    }
}
