package com.library.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemberTest {

    @Test
    void shouldCreateNewMemberWithTrimmedNameAndNullId() {
        // Arrange & Act
        Member member = new Member("  Ali Rezaei  ");

        // Assert
        assertNull(member.getId());
        assertEquals("Ali Rezaei", member.getName());
    }

    @Test
    void shouldCreatePersistedMemberWithId() {
        // Arrange & Act
        Member member = new Member(42L, "Sara Ahmadi");

        // Assert
        assertEquals(42L, member.getId());
        assertEquals("Sara Ahmadi", member.getName());
    }

    @Test
    void shouldRejectNullOrBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Member(null));
        assertThrows(IllegalArgumentException.class, () -> new Member("   "));
    }

    @Test
    void shouldRejectNonPositiveId() {
        assertThrows(IllegalArgumentException.class, () -> new Member(0L, "Ali"));
        assertThrows(IllegalArgumentException.class, () -> new Member(-5L, "Ali"));

        Member member = new Member("Ali");
        assertThrows(IllegalArgumentException.class, () -> member.setId(0L));
    }
}
