package com.library.repository;

import com.library.model.Loan;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for persisting and querying {@link Loan} entities.
 */
public interface LoanRepository {

    Loan save(Loan loan);

    Optional<Loan> findById(Long id);

    /**
     * Finds the currently active loan (return_date IS NULL) for the given book ISBN, if any.
     */
    Optional<Loan> findActiveByBookIsbn(String bookIsbn);

    /**
     * Returns all historical and active loans for the given book ISBN.
     */
    List<Loan> findByBookIsbn(String bookIsbn);

    /**
     * Returns all historical and active loans for the given member ID.
     */
    List<Loan> findByMemberId(Long memberId);

    /**
     * Returns all loans ordered from most recent to oldest.
     */
    List<Loan> findAll();

    void update(Loan loan);

    boolean hasActiveLoan(String bookIsbn);
}
