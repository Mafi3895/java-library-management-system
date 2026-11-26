package com.library.service.fake;

import com.library.model.Loan;
import com.library.repository.LoanRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory test double for {@link LoanRepository} used to unit-test services independently of SQLite.
 */
public class InMemoryLoanRepository implements LoanRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Map<Long, Loan> storage = new HashMap<>();

    @Override
    public Loan save(Loan loan) {
        long id = loan.getId() != null ? loan.getId() : idGenerator.getAndIncrement();
        loan.setId(id);
        storage.put(id, copy(loan));
        return copy(loan);
    }

    @Override
    public Optional<Loan> findById(Long id) {
        Loan found = storage.get(id);
        return found == null ? Optional.empty() : Optional.of(copy(found));
    }

    @Override
    public Optional<Loan> findActiveByBookIsbn(String bookIsbn) {
        return storage.values().stream()
                .filter(loan -> loan.getBookIsbn().equals(bookIsbn) && loan.isActive())
                .findFirst()
                .map(this::copy);
    }

    @Override
    public List<Loan> findByBookIsbn(String bookIsbn) {
        return storage.values().stream()
                .filter(loan -> loan.getBookIsbn().equals(bookIsbn))
                .sorted(Comparator.comparing(Loan::getBorrowDate).reversed()
                        .thenComparing(Comparator.comparingLong(Loan::getId).reversed()))
                .map(this::copy)
                .toList();
    }

    @Override
    public List<Loan> findByMemberId(Long memberId) {
        return storage.values().stream()
                .filter(loan -> loan.getMemberId().equals(memberId))
                .sorted(Comparator.comparing(Loan::getBorrowDate).reversed()
                        .thenComparing(Comparator.comparingLong(Loan::getId).reversed()))
                .map(this::copy)
                .toList();
    }

    @Override
    public List<Loan> findAll() {
        return storage.values().stream()
                .sorted(Comparator.comparing(Loan::getBorrowDate).reversed()
                        .thenComparing(Comparator.comparingLong(Loan::getId).reversed()))
                .map(this::copy)
                .toList();
    }

    @Override
    public void update(Loan loan) {
        storage.put(loan.getId(), copy(loan));
    }

    @Override
    public boolean hasActiveLoan(String bookIsbn) {
        return findActiveByBookIsbn(bookIsbn).isPresent();
    }

    private Loan copy(Loan loan) {
        return new Loan(
                loan.getId(),
                loan.getBookIsbn(),
                loan.getMemberId(),
                loan.getBorrowDate(),
                loan.getReturnDate()
        );
    }
}
