package com.library.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Utility methods for formatting and parsing ISO-8601 dates used in SQLite storage and display.
 */
public final class DateUtils {

    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private DateUtils() {
        // Utility class
    }

    public static String format(LocalDate date) {
        return date == null ? null : date.format(ISO_DATE_FORMATTER);
    }

    public static LocalDate parse(String dateText) {
        if (dateText == null || dateText.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(dateText.trim(), ISO_DATE_FORMATTER);
    }
}
