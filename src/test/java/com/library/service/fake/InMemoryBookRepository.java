package com.library.service.fake;

import com.library.model.Book;
import com.library.model.BookSearchCriteria;
import com.library.model.BookSortField;
import com.library.model.SortDirection;
import com.library.repository.BookRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory test double for {@link BookRepository} used to unit-test services independently of SQLite.
 */
public class InMemoryBookRepository implements BookRepository {

    private final Map<String, Book> storage = new HashMap<>();

    @Override
    public void save(Book book) {
        storage.put(book.getIsbn(), copy(book));
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        Book found = storage.get(isbn);
        return found == null ? Optional.empty() : Optional.of(copy(found));
    }

    @Override
    public List<Book> findAll() {
        return findAll(BookSortField.TITLE, SortDirection.ASC);
    }

    @Override
    public List<Book> findAll(BookSortField sortField, SortDirection sortDirection) {
        return search(BookSearchCriteria.empty(), sortField, sortDirection);
    }

    @Override
    public List<Book> search(BookSearchCriteria criteria, BookSortField sortField, SortDirection sortDirection) {
        BookSearchCriteria effective = criteria != null ? criteria : BookSearchCriteria.empty();

        return storage.values().stream()
                .filter(book -> !book.isDeleted())
                .filter(book -> matchesCriteria(book, effective))
                .sorted(buildComparator(sortField, sortDirection))
                .map(this::copy)
                .toList();
    }

    @Override
    public void update(Book book) {
        storage.put(book.getIsbn(), copy(book));
    }

    @Override
    public void updateAvailability(String isbn, boolean available) {
        Book existing = storage.get(isbn);
        if (existing != null) {
            existing.setAvailable(available);
        }
    }

    @Override
    public void delete(String isbn) {
        Book existing = storage.get(isbn);
        if (existing != null) {
            existing.markAsDeleted();
        }
    }

    @Override
    public boolean existsByIsbn(String isbn) {
        return storage.containsKey(isbn);
    }

    private boolean matchesCriteria(Book book, BookSearchCriteria criteria) {
        if (criteria.getTitle() != null &&
                !book.getTitle().toLowerCase(Locale.ROOT).contains(criteria.getTitle().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (criteria.getAuthor() != null &&
                !book.getAuthor().toLowerCase(Locale.ROOT).contains(criteria.getAuthor().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (criteria.getPublicationYear() != null &&
                book.getPublicationYear() != criteria.getPublicationYear()) {
            return false;
        }
        if (criteria.getAvailable() != null &&
                book.isAvailable() != criteria.getAvailable()) {
            return false;
        }
        return true;
    }

    private Comparator<Book> buildComparator(BookSortField sortField, SortDirection sortDirection) {
        BookSortField field = sortField != null ? sortField : BookSortField.TITLE;
        SortDirection direction = sortDirection != null ? sortDirection : SortDirection.ASC;

        Comparator<Book> comparator = switch (field) {
            case TITLE -> Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER);
            case AUTHOR -> Comparator.comparing(Book::getAuthor, String.CASE_INSENSITIVE_ORDER);
            case PUBLICATION_YEAR -> Comparator.comparingInt(Book::getPublicationYear);
        };
        if (direction == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(Book::getIsbn);
    }

    private Book copy(Book book) {
        return new Book(
                book.getIsbn(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublicationYear(),
                book.isAvailable(),
                book.isDeleted()
        );
    }
}
