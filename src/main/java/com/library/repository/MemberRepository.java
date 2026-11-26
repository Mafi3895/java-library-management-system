package com.library.repository;

import com.library.model.Member;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for persisting and querying {@link Member} entities.
 */
public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long id);

    /**
     * Finds all members matching the given name (case-insensitive).
     * Member names are not assumed to be unique.
     */
    List<Member> findByName(String name);

    List<Member> findAll();
}
