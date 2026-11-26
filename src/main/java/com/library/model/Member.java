package com.library.model;

import java.util.Objects;

/**
 * Domain entity representing a library member who can borrow books.
 * Member names are not assumed to be unique; each member is identified by their ID.
 */
public class Member {

    private Long id;
    private String name;

    /**
     * Creates a new member before persistence (ID will be assigned by the database).
     */
    public Member(String name) {
        this(null, name);
    }

    /**
     * Creates a member with an existing database ID.
     */
    public Member(Long id, String name) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("Member ID must be a positive number.");
        }
        this.id = id;
        this.name = validateName(name);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Member ID must be a positive number.");
        }
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = validateName(name);
    }

    public static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Member name must not be null or empty.");
        }
        return name.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Member member)) {
            return false;
        }
        if (id != null && member.id != null) {
            return Objects.equals(id, member.id);
        }
        return Objects.equals(id, member.id) && Objects.equals(name, member.name);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Member{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
