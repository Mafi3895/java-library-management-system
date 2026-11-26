package com.library.model;

/**
 * Immutable search filter criteria for querying books.
 * Any null or blank filter field is ignored during search.
 */
public class BookSearchCriteria {

    private final String title;
    private final String author;
    private final Integer publicationYear;
    private final Boolean available;

    public BookSearchCriteria(String title, String author, Integer publicationYear, Boolean available) {
        this.title = normalizeText(title);
        this.author = normalizeText(author);
        this.publicationYear = publicationYear;
        this.available = available;
    }

    public static BookSearchCriteria empty() {
        return new BookSearchCriteria(null, null, null, null);
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public Boolean getAvailable() {
        return available;
    }

    private static String normalizeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
