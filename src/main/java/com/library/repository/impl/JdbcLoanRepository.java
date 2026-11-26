package com.library.repository.impl;

import com.library.database.DatabaseManager;
import com.library.exception.DatabaseException;
import com.library.model.Loan;
import com.library.repository.LoanRepository;
import com.library.util.DateUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link LoanRepository} backed by SQLite.
 */
public class JdbcLoanRepository implements LoanRepository {

    private static final String BASE_SELECT =
            "SELECT id, book_isbn, member_id, borrow_date, return_date FROM loans";

    private final DatabaseManager databaseManager;

    public JdbcLoanRepository(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager must not be null.");
        }
        this.databaseManager = databaseManager;
    }

    @Override
    public Loan save(Loan loan) {
        String sql = "INSERT INTO loans (book_isbn, member_id, borrow_date, return_date) VALUES (?, ?, ?, ?)";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, loan.getBookIsbn());
                ps.setLong(2, loan.getMemberId());
                ps.setString(3, DateUtils.format(loan.getBorrowDate()));
                if (loan.getReturnDate() != null) {
                    ps.setString(4, DateUtils.format(loan.getReturnDate()));
                } else {
                    ps.setNull(4, Types.VARCHAR);
                }
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        loan.setId(keys.getLong(1));
                        return loan;
                    }
                    throw new DatabaseException("Failed to retrieve generated ID for saved loan.");
                }
            }
        });
    }

    @Override
    public Optional<Loan> findById(Long id) {
        String sql = BASE_SELECT + " WHERE id = ?";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
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
    public Optional<Loan> findActiveByBookIsbn(String bookIsbn) {
        String sql = BASE_SELECT + " WHERE book_isbn = ? AND return_date IS NULL";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, bookIsbn);
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
    public List<Loan> findByBookIsbn(String bookIsbn) {
        String sql = BASE_SELECT + " WHERE book_isbn = ? ORDER BY borrow_date DESC, id DESC";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, bookIsbn);
                try (ResultSet rs = ps.executeQuery()) {
                    return mapRows(rs);
                }
            }
        });
    }

    @Override
    public List<Loan> findByMemberId(Long memberId) {
        String sql = BASE_SELECT + " WHERE member_id = ? ORDER BY borrow_date DESC, id DESC";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, memberId);
                try (ResultSet rs = ps.executeQuery()) {
                    return mapRows(rs);
                }
            }
        });
    }

    @Override
    public List<Loan> findAll() {
        String sql = BASE_SELECT + " ORDER BY borrow_date DESC, id DESC";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                return mapRows(rs);
            }
        });
    }

    @Override
    public void update(Loan loan) {
        String sql = "UPDATE loans SET return_date = ? WHERE id = ?";

        databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (loan.getReturnDate() != null) {
                    ps.setString(1, DateUtils.format(loan.getReturnDate()));
                } else {
                    ps.setNull(1, Types.VARCHAR);
                }
                ps.setLong(2, loan.getId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public boolean hasActiveLoan(String bookIsbn) {
        String sql = "SELECT 1 FROM loans WHERE book_isbn = ? AND return_date IS NULL LIMIT 1";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, bookIsbn);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    private List<Loan> mapRows(ResultSet rs) throws SQLException {
        List<Loan> loans = new ArrayList<>();
        while (rs.next()) {
            loans.add(mapRow(rs));
        }
        return loans;
    }

    private Loan mapRow(ResultSet rs) throws SQLException {
        return new Loan(
                rs.getLong("id"),
                rs.getString("book_isbn"),
                rs.getLong("member_id"),
                DateUtils.parse(rs.getString("borrow_date")),
                DateUtils.parse(rs.getString("return_date"))
        );
    }
}
