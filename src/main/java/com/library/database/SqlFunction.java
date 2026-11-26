package com.library.database;

import java.sql.SQLException;

/**
 * Functional interface representing a JDBC operation that may throw {@link SQLException}.
 */
@FunctionalInterface
public interface SqlFunction<T, R> {

    R apply(T input) throws SQLException;
}
