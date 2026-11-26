package com.library.repository.impl;

import com.library.database.DatabaseManager;
import com.library.model.Book;
import com.library.model.BookSearchCriteria;
import com.library.model.BookSortField;
import com.library.model.SortDirection;
import com.library.repository.BookRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link BookRepository} backed by SQLite.
 */
public class JdbcBookRepository implements BookRepository {

    private static final String BASE_SELECT =
            "SELECT isbn, title, author, publication_year, available, is_deleted FROM books";

    private final DatabaseManager databaseManager;

    public JdbcBookRepository(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager must not be null.");
        }
        this.databaseManager = databaseManager;
    }

    @Override
    public void save(Book book) {
        String sql = "INSERT INTO books (isbn, title, author, publication_year, available, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, book.getIsbn());
                ps.setString(2, book.getTitle());
                ps.setString(3, book.getAuthor());
                ps.setInt(4, book.getPublicationYear());
                ps.setInt(5, book.isAvailable() ? 1 : 0);
                ps.setInt(6, book.isDeleted() ? 1 : 0);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        String sql = BASE_SELECT + " WHERE isbn = ?";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, isbn);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            }
        });
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
        BookSearchCriteria effectiveCriteria = criteria != null ? criteria : BookSearchCriteria.empty();
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE is_deleted = 0");
        List<Object> parameters = new ArrayList<>();

        if (effectiveCriteria.getTitle() != null) {
            sql.append(" AND LOWER(title) LIKE LOWER(?) ESCAPE '\\'");
            parameters.add("%" + escapeLikePattern(effectiveCriteria.getTitle()) + "%");
        }
        if (effectiveCriteria.getAuthor() != null) {
            sql.append(" AND LOWER(author) LIKE LOWER(?) ESCAPE '\\'");
            parameters.add("%" + escapeLikePattern(effectiveCriteria.getAuthor()) + "%");
        }
        if (effectiveCriteria.getPublicationYear() != null) {
            sql.append(" AND publication_year = ?");
            parameters.add(effectiveCriteria.getPublicationYear());
        }
        if (effectiveCriteria.getAvailable() != null) {
            sql.append(" AND available = ?");
            parameters.add(effectiveCriteria.getAvailable() ? 1 : 0);
        }

        sql.append(" ORDER BY ").append(resolveOrderByClause(sortField, sortDirection));

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                bindParameters(ps, parameters);
                try (ResultSet rs = ps.executeQuery()) {
                    List<Book> books = new ArrayList<>();
                    while (rs.next()) {
                        books.add(mapRow(rs));
                    }
                    return books;
                }
            }
        });
    }

    @Override
    public void update(Book book) {
        String sql = "UPDATE books SET title = ?, author = ?, publication_year = ?, available = ?, is_deleted = ? " +
                "WHERE isbn = ?";

        databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, book.getTitle());
                ps.setString(2, book.getAuthor());
                ps.setInt(3, book.getPublicationYear());
                ps.setInt(4, book.isAvailable() ? 1 : 0);
                ps.setInt(5, book.isDeleted() ? 1 : 0);
                ps.setString(6, book.getIsbn());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void updateAvailability(String isbn, boolean available) {
        String sql = "UPDATE books SET available = ? WHERE isbn = ?";

        databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, available ? 1 : 0);
                ps.setString(2, isbn);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void delete(String isbn) {
        String sql = "UPDATE books SET is_deleted = 1 WHERE isbn = ?";

        databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, isbn);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public boolean existsByIsbn(String isbn) {
        String sql = "SELECT 1 FROM books WHERE isbn = ? LIMIT 1";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, isbn);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    private void bindParameters(PreparedStatement ps, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            Object param = parameters.get(i);
            int index = i + 1;
            if (param instanceof String str) {
                ps.setString(index, str);
            } else if (param instanceof Integer num) {
                ps.setInt(index, num);
            } else {
                ps.setObject(index, param);
            }
        }
    }

    private String resolveOrderByClause(BookSortField sortField, SortDirection sortDirection) {
        BookSortField field = sortField != null ? sortField : BookSortField.TITLE;
        SortDirection direction = sortDirection != null ? sortDirection : SortDirection.ASC;

        String column = switch (field) {
            case TITLE -> "title COLLATE NOCASE";
            case AUTHOR -> "author COLLATE NOCASE";
            case PUBLICATION_YEAR -> "publication_year";
        };
        String dir = direction == SortDirection.DESC ? "DESC" : "ASC";

        return column + " " + dir + ", isbn ASC";
    }

    private String escapeLikePattern(String raw) {
        return raw
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private Book mapRow(ResultSet rs) throws SQLException {
        return new Book(
                rs.getString("isbn"),
                rs.getString("title"),
                rs.getString("author"),
                rs.getInt("publication_year"),
                rs.getInt("available") == 1,
                rs.getInt("is_deleted") == 1
        );
    }
}
