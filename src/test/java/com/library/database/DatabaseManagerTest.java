package com.library.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private String jdbcUrl;
    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("test-library.db");
        jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        databaseManager = new DatabaseManager(jdbcUrl);
        databaseManager.initializeDatabase();
    }

    @Test
    void shouldInitializeAllRequiredTablesAndActiveLoanIndex() throws SQLException {
        // Arrange
        Set<String> tables = new HashSet<>();
        Set<String> indexes = new HashSet<>();

        // Act
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
            }
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'index' AND name NOT LIKE 'sqlite_%'")) {
                while (rs.next()) {
                    indexes.add(rs.getString("name"));
                }
            }
        }

        // Assert
        assertTrue(tables.contains("books"));
        assertTrue(tables.contains("members"));
        assertTrue(tables.contains("loans"));
        assertTrue(indexes.contains("idx_loans_unique_active_book"));
    }

    @Test
    void shouldEnableForeignKeysOnEveryConnection() throws SQLException {
        // Act & Assert
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void shouldPersistDataAcrossDatabaseManagerInstancesAndIdempotentInit() throws SQLException {
        // Arrange
        insertSampleBook("978-0134685991", "Effective Java", "Joshua Bloch", 2018);

        // Act: simulate application restart with a new DatabaseManager instance
        DatabaseManager restartedManager = new DatabaseManager(jdbcUrl);
        restartedManager.initializeDatabase();

        // Assert
        try (Connection conn = restartedManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT title FROM books WHERE isbn = ?")) {
            ps.setString(1, "978-0134685991");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Effective Java", rs.getString("title"));
            }
        }
    }

    @Test
    void shouldEnforceUniqueIsbnConstraint() throws SQLException {
        // Arrange
        insertSampleBook("978-0132350884", "Clean Code", "Robert C. Martin", 2008);

        // Act & Assert
        assertThrows(SQLException.class, () ->
                insertSampleBook("978-0132350884", "Clean Code 2", "Robert C. Martin", 2009));
    }

    @Test
    void shouldEnforceForeignKeyConstraintsOnLoans() {
        // Act & Assert: non-existent book and member
        assertThrows(SQLException.class, () -> insertLoan("NON-EXISTENT-ISBN", 999L, "2025-01-10", null));
    }

    @Test
    void shouldEnforceSingleActiveLoanPerBookAndAllowSubsequentLoanAfterReturn() throws SQLException {
        // Arrange
        insertSampleBook("978-0134685991", "Effective Java", "Joshua Bloch", 2018);
        long memberId = insertSampleMember("Ali Rezaei");
        insertLoan("978-0134685991", memberId, "2025-01-10", null);

        // Act & Assert: second active loan for the same book must fail at DB level
        assertThrows(SQLException.class, () ->
                insertLoan("978-0134685991", memberId, "2025-01-11", null));

        // Return the first loan
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE loans SET return_date = ? WHERE book_isbn = ? AND return_date IS NULL")) {
            ps.setString(1, "2025-01-15");
            ps.setString(2, "978-0134685991");
            assertEquals(1, ps.executeUpdate());
        }

        // Borrowing the same book again after return must succeed
        assertDoesNotThrow(() -> insertLoan("978-0134685991", memberId, "2025-01-16", null));
    }

    @Test
    void shouldPreventPhysicalDeletionOfBookWithLoanHistory() throws SQLException {
        // Arrange
        insertSampleBook("978-0134685991", "Effective Java", "Joshua Bloch", 2018);
        long memberId = insertSampleMember("Sara Ahmadi");
        insertLoan("978-0134685991", memberId, "2025-01-10", "2025-01-20");

        // Act & Assert
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM books WHERE isbn = ?")) {
            ps.setString(1, "978-0134685991");
            assertThrows(SQLException.class, ps::executeUpdate);
        }
    }

    private void insertSampleBook(String isbn, String title, String author, int year) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO books (isbn, title, author, publication_year, available, is_deleted) " +
                             "VALUES (?, ?, ?, ?, 1, 0)")) {
            ps.setString(1, isbn);
            ps.setString(2, title);
            ps.setString(3, author);
            ps.setInt(4, year);
            ps.executeUpdate();
        }
    }

    private long insertSampleMember(String name) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO members (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private void insertLoan(String isbn, long memberId, String borrowDate, String returnDate) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO loans (book_isbn, member_id, borrow_date, return_date) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, isbn);
            ps.setLong(2, memberId);
            ps.setString(3, borrowDate);
            ps.setString(4, returnDate);
            ps.executeUpdate();
        }
    }
}
