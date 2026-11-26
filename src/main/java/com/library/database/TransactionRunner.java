package com.library.database;

import java.util.function.Supplier;

/**
 * Abstraction for executing a unit of work within a database transaction.
 * Allows services to remain decoupled from JDBC while supporting isolated unit testing.
 */
@FunctionalInterface
public interface TransactionRunner {

    <T> T executeInTransaction(Supplier<T> action);

    default void executeInTransactionVoid(Runnable action) {
        executeInTransaction(() -> {
            action.run();
            return null;
        });
    }
}
