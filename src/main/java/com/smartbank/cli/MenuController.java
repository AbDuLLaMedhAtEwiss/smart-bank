package com.smartbank.cli;

import com.smartbank.config.AppConfig;
import com.smartbank.demo.ConcurrencyDemo;
import com.smartbank.exception.BankException;
import com.smartbank.exception.ValidationException;
import com.smartbank.model.Account;
import com.smartbank.model.AccountType;
import com.smartbank.model.CurrentAccount;
import com.smartbank.model.SavingsAccount;
import com.smartbank.model.Transaction;
import com.smartbank.service.BankService;
import com.smartbank.util.Money;
import java.io.Console;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Text user interface. Contains no business logic: it only reads input, calls {@link BankService}, prints. */
public final class MenuController {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final BankService bank;
    private final AppConfig config;
    private final Scanner in = new Scanner(System.in);

    public MenuController(BankService bank, AppConfig config) {
        this.bank = bank;
        this.config = config;
    }

    public void run() {
        System.out.println();
        System.out.println("=====================================");
        System.out.println("   SMART BANK - Account Management   ");
        System.out.println("=====================================");
        try {
            while (true) {
                System.out.println();
                System.out.println("1) Open account");
                System.out.println("2) Login");
                System.out.println("3) Administrator");
                System.out.println("4) Run concurrency stress test");
                System.out.println("0) Exit");
                String choice = ask("Choose");
                try {
                    switch (choice) {
                        case "1" -> openAccount();
                        case "2" -> login();
                        case "3" -> admin();
                        case "4" -> stressTest();
                        case "0" -> {
                            System.out.println("Saving data... goodbye.");
                            return;
                        }
                        default -> System.out.println("Unknown option.");
                    }
                } catch (BankException e) {
                    System.out.println("x " + e.getMessage());
                }
            }
        } catch (NoSuchElementException endOfInput) {
            System.out.println("\nInput closed, exiting.");
        }
    }

    // ---------------------------------------------------------------- main menu actions

    private void openAccount() {
        String kind = ask("Account type  1) Savings  2) Current");
        AccountType type = switch (kind) {
            case "1" -> AccountType.SAVINGS;
            case "2" -> AccountType.CURRENT;
            default -> throw new ValidationException("Please choose 1 or 2");
        };
        String name = ask("Full name");
        String email = ask("E-mail");
        char[] pin = askSecret("Choose a PIN (4-6 digits)");
        char[] confirm = askSecret("Repeat PIN");
        if (!java.util.Arrays.equals(pin, confirm)) {
            throw new ValidationException("PINs do not match");
        }
        String initial = ask("Initial deposit (leave empty for none)");
        BigDecimal amount = initial.isBlank() ? Money.ZERO : Money.parse(initial);

        Account account = bank.openAccount(type, name, email, pin, amount);
        System.out.println("Account opened. Your account number is " + account.getNumber());
    }

    private void login() {
        String number = ask("Account number");
        char[] pin = askSecret("PIN");
        Account account = bank.authenticate(number, pin);
        System.out.println("Welcome, " + account.getOwner().name() + "!");
        sessionMenu(account);
    }

    private void stressTest() {
        try {
            ConcurrencyDemo.run(10, 32, 50_000, System.out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Interrupted.");
        }
    }

    // ---------------------------------------------------------------- customer session

    private void sessionMenu(Account account) {
        String number = account.getNumber();
        while (true) {
            System.out.println();
            System.out.println("--- " + number + " ---");
            System.out.println("1) Balance   2) Deposit   3) Withdraw   4) Transfer");
            System.out.println("5) Mini statement   6) Account details   0) Logout");
            String choice = ask("Choose");
            try {
                switch (choice) {
                    case "1" -> System.out.println("Balance: " + Money.format(bank.getBalance(number)));
                    case "2" -> {
                        Transaction t = bank.deposit(number, Money.parse(ask("Amount")));
                        System.out.println("Deposited. New balance: " + Money.format(t.balanceAfter()));
                    }
                    case "3" -> {
                        Transaction t = bank.withdraw(number, Money.parse(ask("Amount")));
                        System.out.println("Withdrawn (fee " + Money.format(t.fee()) + "). New balance: "
                                + Money.format(t.balanceAfter()));
                    }
                    case "4" -> {
                        String to = ask("Destination account number");
                        Transaction t = bank.transfer(number, to, Money.parse(ask("Amount")));
                        System.out.println("Transferred (fee " + Money.format(t.fee()) + "). New balance: "
                                + Money.format(t.balanceAfter()));
                    }
                    case "5" -> printStatement(bank.getStatement(number, 10));
                    case "6" -> printDetails(account);
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("Unknown option.");
                }
            } catch (BankException e) {
                System.out.println("x " + e.getMessage());
            }
        }
    }

    private void printDetails(Account account) {
        // Exhaustive pattern-matching switch over the sealed Account hierarchy (Java 21).
        String details = switch (account) {
            case SavingsAccount s -> "Savings account, interest %s%% per year"
                    .formatted(s.getAnnualRate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString());
            case CurrentAccount c -> "Current account, overdraft limit " + Money.format(c.getOverdraftLimit());
        };
        System.out.println("Owner : " + account.getOwner().name() + " <" + account.getOwner().email() + ">");
        System.out.println("Type  : " + details);
        System.out.println("Opened: " + TIME.format(account.getCreatedAt()));
    }

    private void printStatement(List<Transaction> entries) {
        if (entries.isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }
        System.out.printf("%-19s %-13s %12s %8s %12s  %s%n", "Time", "Type", "Amount", "Fee", "Balance", "Other party");
        for (Transaction t : entries) {
            System.out.printf("%-19s %-13s %12s %8s %12s  %s%n",
                    TIME.format(t.timestamp()), t.type(), Money.format(t.amount()), Money.format(t.fee()),
                    Money.format(t.balanceAfter()), t.counterparty() == null ? "" : t.counterparty());
        }
    }

    // ---------------------------------------------------------------- administrator

    private void admin() {
        if (!config.adminPassword().equals(new String(askSecret("Administrator password")))) {
            throw new ValidationException("Wrong administrator password");
        }
        while (true) {
            System.out.println();
            System.out.println("--- Administrator ---");
            System.out.println("1) List accounts   2) Apply monthly interest now   3) Unlock account");
            System.out.println("4) Bank totals     5) Save now                     0) Back");
            String choice = ask("Choose");
            try {
                switch (choice) {
                    case "1" -> listAccounts();
                    case "2" -> System.out.println("Interest paid: " + Money.format(bank.applyMonthlyInterest()));
                    case "3" -> {
                        bank.unlockAccount(ask("Account number"));
                        System.out.println("Account unlocked.");
                    }
                    case "4" -> System.out.println("Total deposits: " + Money.format(bank.totalBalance())
                            + " | fees earned this session: " + Money.format(bank.totalFees()));
                    case "5" -> {
                        bank.save();
                        System.out.println("Saved.");
                    }
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("Unknown option.");
                }
            } catch (BankException e) {
                System.out.println("x " + e.getMessage());
            }
        }
    }

    private void listAccounts() {
        List<Account> all = bank.listAccounts();
        if (all.isEmpty()) {
            System.out.println("No accounts yet.");
            return;
        }
        System.out.printf("%-11s %-8s %-24s %14s %s%n", "Number", "Type", "Owner", "Balance", "Status");
        for (Account a : all) {
            System.out.printf("%-11s %-8s %-24s %14s %s%n", a.getNumber(), a.getType(), a.getOwner().name(),
                    Money.format(a.getBalance()), a.isLocked() ? "LOCKED" : "active");
        }
    }

    // ---------------------------------------------------------------- input helpers

    private String ask(String label) {
        System.out.print(label + ": ");
        return in.nextLine().strip();
    }

    private char[] askSecret(String label) {
        Console console = System.console();
        if (console != null) {
            char[] secret = console.readPassword("%s: ", label);
            if (secret == null) {
                throw new NoSuchElementException();
            }
            return secret;
        }
        return ask(label + " (input is visible: no console)").toCharArray();
    }
}
