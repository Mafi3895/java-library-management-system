# Java Library Management System

A clean, maintainable, console-based **Library Management System** built with **Java 17 Core**, **JDBC**, **SQLite**, **Maven**, and **JUnit 5** — without Spring, Hibernate, or any heavyweight enterprise frameworks.

---

## 1. Project Overview

This application simulates a small library catalog and circulation desk. It allows library staff and users to register, browse, sort, search, update, and soft-delete books, as well as borrow and return books while preserving a complete borrowing history in a persistent SQLite database.

The codebase demonstrates core software engineering fundamentals:
- Clean Code and Object-Oriented Programming (OOP)
- Pragmatic SOLID design and Layered Architecture
- JDBC connection, prepared statement, and transaction management
- Database-level integrity constraints combined with service-level validation
- Isolated unit tests (via in-memory repository fakes) and real SQLite integration tests

---

## 2. Features

- **Register a New Book**: Validates ISBN, title, author, and publication year; enforces ISBN uniqueness; initializes new books as available.
- **View All Books**: Lists all non-deleted books with sorting by title, author, or publication year (ascending or descending).
- **Advanced Search & Filtering**: Filter simultaneously by title substring, author substring, publication year, and availability status (excluding soft-deleted books), using parameterized `PreparedStatement` queries.
- **Update Book Information**: Updates title, author, and publication year while strictly keeping ISBN immutable.
- **Soft Delete a Book**: Marks eligible books as deleted (`is_deleted = 1`) only when no active loan exists, excluding them from normal catalog queries while preserving historical loans.
- **Borrow a Book**: Executes atomically in a JDBC transaction — validates book state, finds or creates the borrower record, creates a new active loan, and marks the book unavailable.
- **Return a Book**: Executes atomically in a JDBC transaction — verifies the active loan, records the return date, and restores book availability.
- **View Borrowing History**: Displays full or per-book loan history including borrower name, dates, loan status, and `[DELETED]` indicators for soft-deleted books.

---

## 3. Technology Stack

| Component | Technology |
|---|---|
| Language | Java 17 (Core Standard API, `java.time`) |
| Build Tool | Apache Maven |
| Database | SQLite |
| Data Access | JDBC (`org.xerial:sqlite-jdbc`) |
| Testing | JUnit 5 (`org.junit.jupiter:junit-jupiter`) |
| Architecture | Layered Architecture |
| Interface | Menu-driven Console Application |
| Version Control | Git |

No external application frameworks (Spring, Spring Boot, Hibernate, JPA, etc.) are used.

---

## 4. Prerequisites

- **JDK 17** or newer (compiled with `--release 17`)
- **Apache Maven 3.8+**
- **Git**

---

## 5. Installation

```bash
git clone https://github.com/Mafi3895/java-library-management-system.git
cd java-library-management-system
mvn clean compile
```

---

## 6. How to Run the Application

Run directly via Maven:

```bash
mvn exec:java
```

Or package and run the compiled classes/JAR:

```bash
mvn clean package
mvn exec:java -Dexec.mainClass="com.library.Main"
```

By default, the application automatically creates and initializes a persistent SQLite database file named `library.db` in the working directory. You may also pass a custom JDBC URL as the first command-line argument:

```bash
mvn exec:java -Dexec.args="jdbc:sqlite:custom-library.db"
```

---

## 7. How to Run Tests

Execute the entire unit and integration test suite (57 tests):

```bash
mvn clean test
```

- **Unit tests** (`BookTest`, `MemberTest`, `LoanTest`, `BookServiceTest`, `LoanServiceTest`) run completely in-memory using lightweight fake repositories.
- **Integration tests** (`DatabaseManagerTest`, `JdbcBookRepositoryTest`, `JdbcMemberRepositoryTest`, `JdbcLoanRepositoryTest`, `LoanServiceIntegrationTest`, `MainTest`) run against isolated temporary SQLite databases created via JUnit 5 `@TempDir`, never touching the production `library.db` file.

---

## 8. Database Design

Schema is defined in [`src/main/resources/database/schema.sql`](src/main/resources/database/schema.sql) and initialized idempotently on startup. SQLite foreign keys (`PRAGMA foreign_keys = ON`) are enforced on every connection via `SQLiteConfig`.

### Tables

1. **`books`**
   - `isbn TEXT PRIMARY KEY` (unique, immutable natural identifier)
   - `title TEXT NOT NULL`
   - `author TEXT NOT NULL`
   - `publication_year INTEGER NOT NULL CHECK (publication_year >= 1)`
   - `available INTEGER NOT NULL CHECK (available IN (0, 1))`
   - `is_deleted INTEGER NOT NULL DEFAULT 0 CHECK (is_deleted IN (0, 1))`

2. **`members`**
   - `id INTEGER PRIMARY KEY AUTOINCREMENT`
   - `name TEXT NOT NULL` *(names are not assumed unique)*

3. **`loans`**
   - `id INTEGER PRIMARY KEY AUTOINCREMENT`
   - `book_isbn TEXT NOT NULL` — `FOREIGN KEY REFERENCES books(isbn) ON DELETE RESTRICT ON UPDATE RESTRICT`
   - `member_id INTEGER NOT NULL` — `FOREIGN KEY REFERENCES members(id) ON DELETE RESTRICT ON UPDATE RESTRICT`
   - `borrow_date DATE NOT NULL` (stored as ISO-8601 `YYYY-MM-DD`)
   - `return_date DATE NULL` (`NULL` while active; `CHECK (return_date IS NULL OR return_date >= borrow_date)`)

### Database Integrity Protections

- **Single Active Loan Guarantee**: A SQLite partial unique index prevents a book from ever having more than one active loan, even under concurrent borrow attempts:
  ```sql
  CREATE UNIQUE INDEX IF NOT EXISTS idx_loans_unique_active_book
      ON loans(book_isbn)
      WHERE return_date IS NULL;
  ```
- **Referential Integrity**: `ON DELETE RESTRICT` prevents physical deletion of books or members that have historical loan records.
- **Transactional Consistency**: Borrowing and returning run inside JDBC transactions (`setAutoCommit(false)`, `commit()`, `rollback()`).

---

## 9. Architecture Explanation

The application uses a strict **Layered Architecture**:

1. **Application / Presentation Layer (`com.library.Main`)**: Menu-driven CLI responsible for reading/validating user input and formatting output. Communicates exclusively with `BookService` and `LoanService`.
2. **Service Layer (`com.library.service`)**: Houses all business rules (`BookService`, `LoanService`). Depends only on repository interfaces and `TransactionRunner` — never on concrete JDBC classes or raw SQL.
3. **Repository Layer (`com.library.repository`, `com.library.repository.impl`)**: Defines persistence contracts (`BookRepository`, `MemberRepository`, `LoanRepository`) and their SQLite JDBC implementations using `PreparedStatement` and `try-with-resources`.
4. **Database Layer (`com.library.database`)**: `DatabaseManager` configures SQLite connections (`PRAGMA foreign_keys = ON`), initializes `schema.sql`, and implements `TransactionRunner` using a `ThreadLocal<Connection>` to coordinate multi-repository JDBC transactions cleanly.
5. **Model & Exception Layers (`com.library.model`, `com.library.exception`)**: Pure encapsulated domain entities (`Book`, `Member`, `Loan`), enums, search criteria, and domain-specific exceptions (`BookNotFoundException`, `BookAlreadyExistsException`, `BookNotAvailableException`, `BookAlreadyBorrowedException`, `InvalidBookException`, `LoanNotFoundException`, `BookDeletionException`, `DatabaseException`).

---

## 10. Project Structure

```text
java-library-management-system/
├── pom.xml
├── .gitignore
├── README.md
└── src/
    ├── main/
    │   ├── java/com/library/
    │   │   ├── Main.java
    │   │   ├── database/
    │   │   │   ├── DatabaseManager.java
    │   │   │   ├── SqlFunction.java
    │   │   │   └── TransactionRunner.java
    │   │   ├── exception/
    │   │   │   ├── BookAlreadyBorrowedException.java
    │   │   │   ├── BookAlreadyExistsException.java
    │   │   │   ├── BookDeletionException.java
    │   │   │   ├── BookNotAvailableException.java
    │   │   │   ├── BookNotFoundException.java
    │   │   │   ├── DatabaseException.java
    │   │   │   ├── InvalidBookException.java
    │   │   │   ├── LibraryException.java
    │   │   │   └── LoanNotFoundException.java
    │   │   ├── model/
    │   │   │   ├── Book.java
    │   │   │   ├── BookSearchCriteria.java
    │   │   │   ├── BookSortField.java
    │   │   │   ├── Loan.java
    │   │   │   ├── LoanHistoryEntry.java
    │   │   │   ├── LoanStatus.java
    │   │   │   ├── Member.java
    │   │   │   └── SortDirection.java
    │   │   ├── repository/
    │   │   │   ├── BookRepository.java
    │   │   │   ├── LoanRepository.java
    │   │   │   ├── MemberRepository.java
    │   │   │   └── impl/
    │   │   │       ├── JdbcBookRepository.java
    │   │   │       ├── JdbcLoanRepository.java
    │   │   │       └── JdbcMemberRepository.java
    │   │   ├── service/
    │   │   │   ├── BookService.java
    │   │   │   └── LoanService.java
    │   │   └── util/
    │   │       └── DateUtils.java
    │   └── resources/database/
    │       └── schema.sql
    └── test/
        └── java/com/library/
            ├── MainTest.java
            ├── database/
            │   └── DatabaseManagerTest.java
            ├── model/
            │   ├── BookTest.java
            │   ├── LoanTest.java
            │   └── MemberTest.java
            ├── repository/
            │   ├── JdbcBookRepositoryTest.java
            │   ├── JdbcLoanRepositoryTest.java
            │   └── JdbcMemberRepositoryTest.java
            └── service/
                ├── BookServiceTest.java
                ├── LoanServiceIntegrationTest.java
                ├── LoanServiceTest.java
                └── fake/
                    ├── InMemoryBookRepository.java
                    ├── InMemoryLoanRepository.java
                    └── InMemoryMemberRepository.java
```

---

## 11. Key Architectural & Design Decisions

### 1. Why Layered Architecture?
Separating presentation, business logic, and data access enforces Single Responsibility and Dependency Inversion. Services depend on repository interfaces rather than JDBC classes, allowing business rules to be unit-tested in milliseconds without touching SQLite while keeping SQL strictly encapsulated in `repository.impl`.

### 2. Why JDBC and SQLite?
Using standard JDBC directly demonstrates mastery of connection lifecycle, `PreparedStatement` parameterization, resource management (`try-with-resources`), and explicit transaction boundaries without hiding them behind an ORM. SQLite provides zero-configuration, file-based persistence with full ACID transactions, foreign keys, and partial unique indexes.

### 3. Why `TransactionRunner` with `ThreadLocal<Connection>`?
Borrowing and returning span multiple repositories (`MemberRepository`, `LoanRepository`, `BookRepository`) and must succeed or roll back atomically. Abstracting transaction execution behind `TransactionRunner` lets `LoanService` declare atomic boundaries without importing `java.sql.*`, while `DatabaseManager` shares one JDBC `Connection` across participating repository calls on the same thread.

### 4. Why Is ISBN Immutable?
ISBN is the natural primary key of a book and is referenced by foreign keys in the `loans` table. Making `isbn` a `final` field with no setter (and explicitly rejecting ISBN changes in `BookService`) guarantees referential integrity and prevents historical loan records from becoming orphaned or misattributed.

### 5. Why Maintain Borrowing History Separately & Use Soft Deletion?
- **Separate `loans` table**: Storing each borrow/return cycle as its own row preserves a complete audit trail (who borrowed what, and when it was returned) instead of overwriting previous borrower information on the `books` table.
- **Soft deletion (`is_deleted = 1`)**: Physically deleting a book would either violate foreign key integrity or destroy historical loan records. Soft deletion hides retired books from normal catalog listings and searches while keeping historical loan reports intact and accurate.

---

## 12. Known Limitations

- **Single-copy model**: Each ISBN represents a single physical copy; multiple copies of the same edition are not modeled separately.
- **Member lookup by name**: Borrowing looks up members by case-insensitive name and reuses the first match if found; since member names are not unique, disambiguating two members with identical names would require member ID selection in the CLI.
- **No due dates or fine calculations**: Loans track borrow and return dates only.
- **Single-process SQLite concurrency**: While `busy_timeout` and the partial unique index protect data integrity, SQLite is intended for local/embedded workloads rather than high-concurrency multi-server deployments.

---

## 13. Future Improvements

- Support multiple physical copies per ISBN (introducing a `BookCopy` entity).
- Add member management menu options (register/list members and borrow by Member ID to disambiguate identical names).
- Add due dates, overdue book reports, and configurable loan durations.
- Add pagination to book listing and search results for large catalogs.
- Add a REST API or desktop GUI layer on top of the existing service layer without modifying business logic.
