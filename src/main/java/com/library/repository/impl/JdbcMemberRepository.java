package com.library.repository.impl;

import com.library.database.DatabaseManager;
import com.library.exception.DatabaseException;
import com.library.model.Member;
import com.library.repository.MemberRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link MemberRepository} backed by SQLite.
 */
public class JdbcMemberRepository implements MemberRepository {

    private final DatabaseManager databaseManager;

    public JdbcMemberRepository(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager must not be null.");
        }
        this.databaseManager = databaseManager;
    }

    @Override
    public Member save(Member member) {
        String sql = "INSERT INTO members (name) VALUES (?)";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, member.getName());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        member.setId(keys.getLong(1));
                        return member;
                    }
                    throw new DatabaseException("Failed to retrieve generated ID for saved member.");
                }
            }
        });
    }

    @Override
    public Optional<Member> findById(Long id) {
        String sql = "SELECT id, name FROM members WHERE id = ?";

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
    public List<Member> findByName(String name) {
        String sql = "SELECT id, name FROM members WHERE LOWER(name) = LOWER(?) ORDER BY id ASC";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Member> members = new ArrayList<>();
                    while (rs.next()) {
                        members.add(mapRow(rs));
                    }
                    return members;
                }
            }
        });
    }

    @Override
    public List<Member> findAll() {
        String sql = "SELECT id, name FROM members ORDER BY id ASC";

        return databaseManager.executeWithConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                List<Member> members = new ArrayList<>();
                while (rs.next()) {
                    members.add(mapRow(rs));
                }
                return members;
            }
        });
    }

    private Member mapRow(ResultSet rs) throws SQLException {
        return new Member(
                rs.getLong("id"),
                rs.getString("name")
        );
    }
}
