package com.library.service.fake;

import com.library.model.Member;
import com.library.repository.MemberRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory test double for {@link MemberRepository} used to unit-test services independently of SQLite.
 */
public class InMemoryMemberRepository implements MemberRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Map<Long, Member> storage = new HashMap<>();

    @Override
    public Member save(Member member) {
        long id = member.getId() != null ? member.getId() : idGenerator.getAndIncrement();
        member.setId(id);
        storage.put(id, copy(member));
        return copy(member);
    }

    @Override
    public Optional<Member> findById(Long id) {
        Member found = storage.get(id);
        return found == null ? Optional.empty() : Optional.of(copy(found));
    }

    @Override
    public List<Member> findByName(String name) {
        String normalized = name.trim();
        return storage.values().stream()
                .filter(member -> member.getName().equalsIgnoreCase(normalized))
                .sorted(Comparator.comparingLong(Member::getId))
                .map(this::copy)
                .toList();
    }

    @Override
    public List<Member> findAll() {
        return storage.values().stream()
                .sorted(Comparator.comparingLong(Member::getId))
                .map(this::copy)
                .toList();
    }

    private Member copy(Member member) {
        return new Member(member.getId(), member.getName());
    }
}
