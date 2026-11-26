-- Library Management System - SQLite Schema

CREATE TABLE IF NOT EXISTS books (
    isbn TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    publication_year INTEGER NOT NULL CHECK (publication_year >= 1),
    available INTEGER NOT NULL CHECK (available IN (0, 1)),
    is_deleted INTEGER NOT NULL DEFAULT 0 CHECK (is_deleted IN (0, 1))
);

CREATE TABLE IF NOT EXISTS members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS loans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_isbn TEXT NOT NULL,
    member_id INTEGER NOT NULL,
    borrow_date DATE NOT NULL,
    return_date DATE NULL,
    FOREIGN KEY (book_isbn) REFERENCES books(isbn) ON DELETE RESTRICT ON UPDATE RESTRICT,
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CHECK (return_date IS NULL OR return_date >= borrow_date)
);

-- Enforce at most one active loan (return_date IS NULL) per book at the database level
CREATE UNIQUE INDEX IF NOT EXISTS idx_loans_unique_active_book
    ON loans(book_isbn)
    WHERE return_date IS NULL;

-- Supporting indexes for common query paths
CREATE INDEX IF NOT EXISTS idx_books_is_deleted ON books(is_deleted);
CREATE INDEX IF NOT EXISTS idx_loans_book_isbn ON loans(book_isbn);
CREATE INDEX IF NOT EXISTS idx_loans_member_id ON loans(member_id);
