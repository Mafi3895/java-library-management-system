package com.library;

import com.library.database.DatabaseManager;
import com.library.exception.LibraryException;
import com.library.model.Book;
import com.library.model.BookSearchCriteria;
import com.library.model.BookSortField;
import com.library.model.Loan;
import com.library.model.LoanHistoryEntry;
import com.library.model.SortDirection;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import com.library.repository.MemberRepository;
import com.library.repository.impl.JdbcBookRepository;
import com.library.repository.impl.JdbcLoanRepository;
import com.library.repository.impl.JdbcMemberRepository;
import com.library.service.BookService;
import com.library.service.LoanService;
import com.library.util.DateUtils;

import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/**
 * Application entry point and menu-driven console interface for the Library Management System.
 * Communicates strictly with the service layer for all business operations.
 */
public class Main {

    private final BookService bookService;
    private final LoanService loanService;
    private final Scanner scanner;
    private final PrintStream out;

    public Main(BookService bookService, LoanService loanService, Scanner scanner, PrintStream out) {
        this.bookService = bookService;
        this.loanService = loanService;
        this.scanner = scanner;
        this.out = out;
    }

    public static void main(String[] args) {
        String jdbcUrl = args.length > 0 && !args[0].trim().isEmpty()
                ? args[0].trim()
                : DatabaseManager.DEFAULT_DATABASE_URL;

        DatabaseManager databaseManager = new DatabaseManager(jdbcUrl);
        databaseManager.initializeDatabase();

        BookRepository bookRepository = new JdbcBookRepository(databaseManager);
        MemberRepository memberRepository = new JdbcMemberRepository(databaseManager);
        LoanRepository loanRepository = new JdbcLoanRepository(databaseManager);

        BookService bookService = new BookService(bookRepository, loanRepository);
        LoanService loanService = new LoanService(bookRepository, memberRepository, loanRepository, databaseManager);

        try (Scanner scanner = new Scanner(System.in)) {
            Main app = new Main(bookService, loanService, scanner, System.out);
            app.run();
        }
    }

    public void run() {
        boolean running = true;
        while (running) {
            printMenu();
            String choice = readLine("Select an option: ");
            if (choice == null) {
                break;
            }

            try {
                switch (choice.trim()) {
                    case "1" -> handleRegisterBook();
                    case "2" -> handleViewAllBooks();
                    case "3" -> handleSearchBooks();
                    case "4" -> handleUpdateBook();
                    case "5" -> handleDeleteBook();
                    case "6" -> handleBorrowBook();
                    case "7" -> handleReturnBook();
                    case "8" -> handleViewBorrowingHistory();
                    case "9" -> {
                        out.println("Goodbye!");
                        running = false;
                    }
                    default -> out.println("[Error] Invalid option. Please enter a number between 1 and 9.");
                }
            } catch (LibraryException | IllegalArgumentException | IllegalStateException ex) {
                out.println("[Error] " + ex.getMessage());
            } catch (Exception ex) {
                out.println("[Error] An unexpected error occurred: " + ex.getMessage());
            }
        }
    }

    private void printMenu() {
        out.println();
        out.println("========================================");
        out.println("       LIBRARY MANAGEMENT SYSTEM");
        out.println("========================================");
        out.println();
        out.println("1. Register a new book");
        out.println("2. View all books");
        out.println("3. Search books");
        out.println("4. Update book information");
        out.println("5. Delete a book");
        out.println("6. Borrow a book");
        out.println("7. Return a book");
        out.println("8. View borrowing history");
        out.println("9. Exit");
        out.println();
    }

    private void handleRegisterBook() {
        out.println("\n--- Register a New Book ---");
        String isbn = readLine("ISBN: ");
        String title = readLine("Title: ");
        String author = readLine("Author: ");
        Integer year = readRequiredInt("Publication year: ");
        if (year == null) {
            return;
        }

        Book book = bookService.registerBook(isbn, title, author, year);
        out.println("[Success] Book registered: " + book.getTitle() + " (ISBN: " + book.getIsbn() + ")");
    }

    private void handleViewAllBooks() {
        out.println("\n--- View All Books ---");
        BookSortField sortField = promptSortField();
        SortDirection sortDirection = promptSortDirection();

        List<Book> books = bookService.getAllBooks(sortField, sortDirection);
        printBookTable(books);
    }

    private void handleSearchBooks() {
        out.println("\n--- Search & Filter Books (leave blank to skip a filter) ---");
        String title = readLine("Title contains: ");
        String author = readLine("Author contains: ");
        Integer year = readOptionalInt("Publication year: ");
        if (year != null && year == Integer.MIN_VALUE) {
            return;
        }
        Boolean available = readOptionalAvailability();
        if (available == null && lastAvailabilityInvalid) {
            return;
        }

        BookSortField sortField = promptSortField();
        SortDirection sortDirection = promptSortDirection();

        BookSearchCriteria criteria = new BookSearchCriteria(title, author, year, available);
        List<Book> results = bookService.searchBooks(criteria, sortField, sortDirection);
        printBookTable(results);
    }

    private void handleUpdateBook() {
        out.println("\n--- Update Book Information (ISBN is immutable) ---");
        String isbn = readLine("Enter ISBN of book to update: ");
        Book existing = bookService.getBookByIsbn(isbn);

        out.println("Current details -> Title: " + existing.getTitle()
                + " | Author: " + existing.getAuthor()
                + " | Year: " + existing.getPublicationYear());
        out.println("Press Enter to keep the current value.");

        String newTitle = readLine("New title [" + existing.getTitle() + "]: ");
        if (newTitle == null || newTitle.trim().isEmpty()) {
            newTitle = existing.getTitle();
        }

        String newAuthor = readLine("New author [" + existing.getAuthor() + "]: ");
        if (newAuthor == null || newAuthor.trim().isEmpty()) {
            newAuthor = existing.getAuthor();
        }

        String yearInput = readLine("New publication year [" + existing.getPublicationYear() + "]: ");
        int newYear;
        if (yearInput == null || yearInput.trim().isEmpty()) {
            newYear = existing.getPublicationYear();
        } else {
            try {
                newYear = Integer.parseInt(yearInput.trim());
            } catch (NumberFormatException ex) {
                out.println("[Error] Publication year must be a valid integer.");
                return;
            }
        }

        Book updated = bookService.updateBook(existing.getIsbn(), newTitle, newAuthor, newYear);
        out.println("[Success] Book updated: " + updated.getTitle() + " (ISBN: " + updated.getIsbn() + ")");
    }

    private void handleDeleteBook() {
        out.println("\n--- Delete a Book ---");
        String isbn = readLine("Enter ISBN of book to delete: ");
        bookService.deleteBook(isbn);
        out.println("[Success] Book with ISBN '" + isbn.trim() + "' has been deleted.");
    }

    private void handleBorrowBook() {
        out.println("\n--- Borrow a Book ---");
        String isbn = readLine("Book ISBN: ");
        String borrowerName = readLine("Borrower's name: ");

        Loan loan = loanService.borrowBook(isbn, borrowerName);
        out.println("[Success] Book '" + loan.getBookIsbn() + "' borrowed by '" + borrowerName.trim()
                + "' on " + DateUtils.format(loan.getBorrowDate()) + " (Loan ID: " + loan.getId() + ").");
    }

    private void handleReturnBook() {
        out.println("\n--- Return a Book ---");
        String isbn = readLine("Book ISBN: ");

        Loan returned = loanService.returnBook(isbn);
        out.println("[Success] Book '" + returned.getBookIsbn() + "' returned on "
                + DateUtils.format(returned.getReturnDate()) + " (Loan ID: " + returned.getId() + ").");
    }

    private void handleViewBorrowingHistory() {
        out.println("\n--- Borrowing History ---");
        String isbnFilter = readLine("Filter by Book ISBN (leave blank for all): ");

        List<LoanHistoryEntry> entries = (isbnFilter == null || isbnFilter.trim().isEmpty())
                ? loanService.getBorrowingHistory()
                : loanService.getBorrowingHistoryForBook(isbnFilter);

        if (entries.isEmpty()) {
            out.println("No borrowing records found.");
            return;
        }

        out.printf("%-6s | %-16s | %-28s | %-18s | %-12s | %-12s | %-8s%n",
                "ID", "ISBN", "Book Title", "Borrower", "Borrowed", "Returned", "Status");
        out.println("-".repeat(114));
        for (LoanHistoryEntry entry : entries) {
            String titleDisplay = entry.bookDeleted()
                    ? entry.bookTitle() + " [DELETED]"
                    : entry.bookTitle();
            String returnDateText = entry.returnDate() != null ? DateUtils.format(entry.returnDate()) : "-";
            out.printf("%-6d | %-16s | %-28s | %-18s | %-12s | %-12s | %-8s%n",
                    entry.loanId(),
                    truncate(entry.bookIsbn(), 16),
                    truncate(titleDisplay, 28),
                    truncate(entry.memberName(), 18),
                    DateUtils.format(entry.borrowDate()),
                    returnDateText,
                    entry.status());
        }
    }

    private BookSortField promptSortField() {
        String choice = readLine("Sort by (1=Title, 2=Author, 3=Publication Year) [default 1]: ");
        if (choice == null || choice.trim().isEmpty() || choice.trim().equals("1")) {
            return BookSortField.TITLE;
        }
        return switch (choice.trim()) {
            case "2" -> BookSortField.AUTHOR;
            case "3" -> BookSortField.PUBLICATION_YEAR;
            default -> {
                out.println("[Info] Unrecognized sort field; defaulting to Title.");
                yield BookSortField.TITLE;
            }
        };
    }

    private SortDirection promptSortDirection() {
        String choice = readLine("Order (1=Ascending, 2=Descending) [default 1]: ");
        if (choice != null && choice.trim().equals("2")) {
            return SortDirection.DESC;
        }
        return SortDirection.ASC;
    }

    private void printBookTable(List<Book> books) {
        if (books.isEmpty()) {
            out.println("No books found.");
            return;
        }

        out.printf("%-18s | %-30s | %-22s | %-6s | %-10s%n",
                "ISBN", "Title", "Author", "Year", "Status");
        out.println("-".repeat(98));
        for (Book book : books) {
            out.printf("%-18s | %-30s | %-22s | %-6d | %-10s%n",
                    truncate(book.getIsbn(), 18),
                    truncate(book.getTitle(), 30),
                    truncate(book.getAuthor(), 22),
                    book.getPublicationYear(),
                    book.isAvailable() ? "Available" : "Borrowed");
        }
    }

    private String readLine(String prompt) {
        out.print(prompt);
        if (!scanner.hasNextLine()) {
            return null;
        }
        return scanner.nextLine();
    }

    private Integer readRequiredInt(String prompt) {
        String input = readLine(prompt);
        if (input == null || input.trim().isEmpty()) {
            out.println("[Error] A numeric value is required.");
            return null;
        }
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException ex) {
            out.println("[Error] Invalid number: '" + input.trim() + "'.");
            return null;
        }
    }

    private Integer readOptionalInt(String prompt) {
        String input = readLine(prompt);
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException ex) {
            out.println("[Error] Invalid number: '" + input.trim() + "'.");
            return Integer.MIN_VALUE;
        }
    }

    private boolean lastAvailabilityInvalid;

    private Boolean readOptionalAvailability() {
        lastAvailabilityInvalid = false;
        String input = readLine("Availability (available/borrowed, leave blank for all): ");
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String normalized = input.trim().toLowerCase();
        if (normalized.equals("available") || normalized.equals("a") || normalized.equals("true") || normalized.equals("yes")) {
            return Boolean.TRUE;
        }
        if (normalized.equals("borrowed") || normalized.equals("b") || normalized.equals("false") || normalized.equals("no")) {
            return Boolean.FALSE;
        }
        lastAvailabilityInvalid = true;
        out.println("[Error] Invalid availability filter. Enter 'available', 'borrowed', or leave blank.");
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
