package com.library.repository;

import com.library.database.DatabaseManager;
import com.library.model.Member;
import com.library.repository.impl.JdbcMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JdbcMemberRepositoryTest {

    @TempDir
    Path tempDir;

    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("member-repo-test.db");
        DatabaseManager databaseManager = new DatabaseManager("jdbc:sqlite:" + dbFile.toAbsolutePath());
        databaseManager.initializeDatabase();
        memberRepository = new JdbcMemberRepository(databaseManager);
    }

    @Test
    void shouldSaveMemberWithGeneratedIdAndFindById() {
        // Arrange
        Member member = new Member("Ali Rezaei");

        // Act
        Member saved = memberRepository.save(member);
        Optional<Member> found = memberRepository.findById(saved.getId());

        // Assert
        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);
        assertTrue(found.isPresent());
        assertEquals("Ali Rezaei", found.get().getName());
    }

    @Test
    void shouldAllowMultipleMembersWithSameNameAndFindCaseInsensitively() {
        // Arrange
        Member first = memberRepository.save(new Member("Sara Ahmadi"));
        Member second = memberRepository.save(new Member("sara ahmadi"));
        memberRepository.save(new Member("Reza Karimi"));

        // Act
        List<Member> matched = memberRepository.findByName("SARA AHMADI");

        // Assert
        assertEquals(2, matched.size());
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void shouldReturnAllMembers() {
        // Arrange
        memberRepository.save(new Member("Member One"));
        memberRepository.save(new Member("Member Two"));

        // Act
        List<Member> all = memberRepository.findAll();

        // Assert
        assertEquals(2, all.size());
    }
}
