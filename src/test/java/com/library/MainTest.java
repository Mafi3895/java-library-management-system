package com.library;

import com.library.database.DatabaseManager;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import com.library.repository.MemberRepository;
import com.library.repository.impl.JdbcBookRepository;
import com.library.repository.impl.JdbcLoanRepository;
import com.library.repository.impl.JdbcMemberRepository;
import com.library.service.BookService;
import com.library.service.LoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldHandleFullConsoleWorkflowAndInvalidInputsGracefully() {
        // Arrange
        Path dbFile = tempDir.resolve("console-test.db");
        DatabaseManager databaseManager = new DatabaseManager("jdbc:sqlite:" + dbFile.toAbsolutePath());
        databaseManager.initializeDatabase();

        BookRepository bookRepository = new JdbcBookRepository(databaseManager);
        MemberRepository memberRepository = new JdbcMemberRepository(databaseManager);
        LoanRepository loanRepository = new JdbcLoanRepository(databaseManager);

        BookService bookService = new BookService(bookRepository, loanRepository);
        LoanService loanService = new LoanService(bookRepository, memberRepository, loanRepository, databaseManager);

        String simulatedInput = String.join(System.lineSeparator(),
                "invalid",                  // invalid menu option
                "1",                        // register book
                "978-0134685991",           // isbn
                "Effective Java",           // title
                "Joshua Bloch",             // author
                "2018",                     // year
                "6",                        // borrow book
                "978-0134685991",           // isbn
                "Ali Rezaei",               // borrower
                "5",                        // attempt delete while borrowed (should fail gracefully)
                "978-0134685991",           // isbn
                "7",                        // return book
                "978-0134685991",           // isbn
                "8",                        // view borrowing history
                "",                         // all loans
                "3",                        // search with invalid year
                "",                         // title
                "",                         // author
                "not-a-year",               // invalid year -> handled gracefully
                "3",                        // search with invalid availability
                "",                         // title
                "",                         // author
                "",                         // year
                "maybe",                    // invalid availability -> handled gracefully
                "2",                        // view all books
                "1",                        // sort by title
                "1",                        // ascending
                "9"                         // exit
        ) + System.lineSeparator();

        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner(new ByteArrayInputStream(simulatedInput.getBytes(StandardCharsets.UTF_8)));
             PrintStream out = new PrintStream(outputBuffer, true, StandardCharsets.UTF_8)) {
            Main app = new Main(bookService, loanService, scanner, out);

            // Act
            app.run();
        }

        // Assert
        String output = outputBuffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("[Error] Invalid option."));
        assertTrue(output.contains("[Success] Book registered: Effective Java"));
        assertTrue(output.contains("[Success] Book '978-0134685991' borrowed by 'Ali Rezaei'"));
        assertTrue(output.contains("[Error] Cannot delete book with ISBN '978-0134685991' because it is currently borrowed."));
        assertTrue(output.contains("[Success] Book '978-0134685991' returned on"));
        assertTrue(output.contains("Ali Rezaei"));
        assertTrue(output.contains("RETURNED"));
        assertTrue(output.contains("[Error] Invalid number: 'not-a-year'."));
        assertTrue(output.contains("[Error] Invalid availability filter."));
        assertTrue(output.contains("Available"));
        assertTrue(output.contains("Goodbye!"));
    }
}
