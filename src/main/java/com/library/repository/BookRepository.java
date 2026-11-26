package com.library.repository;

import com.library.model.Book;
import com.library.model.BookSearchCriteria;
import com.library.model.BookSortField;
import com.library.model.SortDirection;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for persisting and querying {@link Book} entities.
 */
public interface BookRepository {

    void save(Book book);

    /**
     * Finds a book by ISBN regardless of soft-deletion state.
     */
    Optional<Book> findByIsbn(String isbn);

    /**
     * Returns all non-deleted books.
     */
    List<Book> findAll();

    /**
     * Returns all non-deleted books sorted by the specified field and direction.
     */
    List<Book> findAll(BookSortField sortField, SortDirection sortDirection);

    /**
     * Searches non-deleted books matching all non-null fields in {@code criteria},
     * optionally sorted by {@code sortField} and {@code sortDirection}.
     */
    List<Book> search(BookSearchCriteria criteria, BookSortField sortField, SortDirection sortDirection);

    void update(Book book);

    void updateAvailability(String isbn, boolean available);

    /**
     * Soft-deletes the book identified by the given ISBN.
     */
    void delete(String isbn);

    boolean existsByIsbn(String isbn);
}
