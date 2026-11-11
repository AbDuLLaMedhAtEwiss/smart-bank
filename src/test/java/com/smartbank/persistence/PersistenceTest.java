package com.smartbank.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbank.exception.StorageException;
import com.smartbank.model.AccountSnapshot;
import com.smartbank.model.AccountType;
import com.smartbank.model.Customer;
import com.smartbank.model.Transaction;
import com.smartbank.model.TransactionType;
import com.smartbank.repository.InMemoryAccountRepository;
import com.smartbank.service.BankService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {
    @TempDir
    Path dir;

    private BankService newBank() {
        return new BankService(new InMemoryAccountRepository(),
                new FileTransactionLog(dir.resolve("transactions.log")),
                new FileSnapshotStore(dir.resolve("accounts.dat")));
    }

    @Test
    void snapshotStoreRoundTripsAllFields() {
        FileSnapshotStore store = new FileSnapshotStore(dir.resolve("accounts.dat"));
        List<AccountSnapshot> original = List.of(
                new AccountSnapshot(AccountType.SAVINGS, 1, new Customer("Ann Lee", "ann@test.com"), "salt:hash",
                        new BigDecimal("10.50"), new BigDecimal("0.04"), false, Instant.ofEpochMilli(1_700_000_000_000L)),
                new AccountSnapshot(AccountType.CURRENT, 2, new Customer("Bob", "bob@test.com"), "s2:h2",
                        new BigDecimal("-20.00"), new BigDecimal("500.00"), true, Instant.ofEpochMilli(1_700_000_001_000L)));

        store.save(original);

        assertEquals(original, store.load());
        assertFalse(Files.exists(dir.resolve("accounts.dat.tmp")), "temp file must not be left behind");
    }

    @Test
    void loadingAMissingFileGivesAnEmptyList() {
        assertTrue(new FileSnapshotStore(dir.resolve("nothing.dat")).load().isEmpty());
    }

    @Test
    void corruptOrForeignAccountsFileIsRejectedLoudly() throws IOException {
        Path file = dir.resolve("accounts.dat");
        Files.writeString(file, "#SOMETHING-ELSE\n");
        assertThrows(StorageException.class, () -> new FileSnapshotStore(file).load());

        Files.writeString(file, "#SMARTBANK-ACCOUNTS v1\nSAVINGS|1|too|few\n");
        assertThrows(StorageException.class, () -> new FileSnapshotStore(file).load());
    }

    @Test
    void everythingSurvivesARestart() {
        String acc;
        long lastIdBefore;
        try (BankService bank = newBank()) {
            acc = bank.openAccount(AccountType.SAVINGS, "Ann", "ann@test.com", "1234".toCharArray(),
                    new BigDecimal("100.00")).getNumber();
            lastIdBefore = bank.deposit(acc, new BigDecimal("25.00")).id();
        }

        try (BankService bank = newBank()) {
            assertEquals(0, new BigDecimal("125.00").compareTo(bank.getBalance(acc)));
            assertEquals(acc, bank.authenticate(acc, "1234".toCharArray()).getNumber()); // PIN hash persisted
            assertEquals(2, bank.getStatement(acc, 10).size());

            Transaction next = bank.withdraw(acc, new BigDecimal("5.00"));
            assertTrue(next.id() > lastIdBefore, "transaction ids must continue after a restart");

            String second = bank.openAccount(AccountType.CURRENT, "Bob", "bob@test.com",
                    "4321".toCharArray(), BigDecimal.ZERO).getNumber();
            assertEquals("SB00000002", second, "account ids must continue after a restart");
        }
    }

    @Test
    void lockedStateSurvivesARestart() {
        String acc;
        try (BankService bank = newBank()) {
            acc = bank.openAccount(AccountType.SAVINGS, "Ann", "ann@test.com", "1234".toCharArray(),
                    BigDecimal.ZERO).getNumber();
            for (int i = 0; i < 3; i++) {
                try {
                    bank.authenticate(acc, "0000".toCharArray());
                } catch (RuntimeException expected) {
                    // wrong PIN / locked
                }
            }
        }
        try (BankService bank = newBank()) {
            assertTrue(bank.listAccounts().get(0).isLocked());
        }
    }

    @Test
    void transactionLogSkipsATornLastLineAndKeepsAppendingCleanly() throws IOException {
        Path file = dir.resolve("transactions.log");
        try (FileTransactionLog log = new FileTransactionLog(file)) {
            log.append(sample(1));
        }
        // simulate a crash in the middle of writing the next entry (no newline, truncated fields)
        Files.writeString(file, "2|1700000000000|DEPO", StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        try (FileTransactionLog log = new FileTransactionLog(file)) {
            assertEquals(1, log.maxTransactionId());
            log.append(sample(2));
            List<Transaction> found = log.findByAccount("SB00000001", 10);
            assertEquals(2, found.size());
            assertEquals(2, found.get(0).id()); // newest first
            assertEquals(1, found.get(1).id());
        }
    }

    private static Transaction sample(long id) {
        return Transaction.builder().id(id).timestamp(Instant.ofEpochMilli(1_700_000_000_000L + id))
                .type(TransactionType.DEPOSIT).accountNumber("SB00000001")
                .amount(new BigDecimal("1.00")).balanceAfter(new BigDecimal("1.00")).build();
    }
}
