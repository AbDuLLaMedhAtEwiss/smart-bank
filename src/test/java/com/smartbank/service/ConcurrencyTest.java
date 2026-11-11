package com.smartbank.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbank.demo.ConcurrencyDemo;
import com.smartbank.model.AccountType;
import com.smartbank.persistence.InMemoryTransactionLog;
import com.smartbank.persistence.SnapshotStore;
import com.smartbank.repository.InMemoryAccountRepository;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ConcurrencyTest {
    private static final PrintStream QUIET = new PrintStream(OutputStream.nullOutputStream());

    @Test
    void totalMoneyIsConservedUnderHeavyRandomLoad() throws Exception {
        ConcurrencyDemo.Result r = ConcurrencyDemo.run(10, 32, 30_000, QUIET);

        assertTrue(r.completedInTime(), "workers did not finish: probable deadlock");
        assertEquals(0, r.unexpectedErrors());
        assertEquals(0, r.expectedTotal().compareTo(r.actualTotal()),
                "expected " + r.expectedTotal() + " but was " + r.actualTotal());
        assertTrue(r.passed());
    }

    @Test
    void oppositeDirectionTransfersNeverDeadlock() {
        BankService bank = new BankService(new InMemoryAccountRepository(), new InMemoryTransactionLog(),
                SnapshotStore.noop());
        char[] pin = "1234".toCharArray();
        BigDecimal start = new BigDecimal("1000000.00");
        String a = bank.openAccount(AccountType.CURRENT, "Ann", "ann@test.com", pin, start).getNumber();
        String b = bank.openAccount(AccountType.CURRENT, "Bob", "bob@test.com", pin, start).getNumber();

        // A->B and B->A at the same time is the classic lock-ordering deadlock; this must finish.
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            int transfersPerThread = 20_000;
            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch go = new CountDownLatch(1);
            java.util.List<Future<?>> futures = new java.util.ArrayList<>();
            for (int t = 0; t < 8; t++) {
                final boolean forward = t % 2 == 0;
                futures.add(pool.submit(() -> {
                    go.await();
                    for (int i = 0; i < transfersPerThread; i++) {
                        if (forward) {
                            bank.transfer(a, b, BigDecimal.ONE);
                        } else {
                            bank.transfer(b, a, BigDecimal.ONE);
                        }
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
            pool.shutdown();
        });

        // 4 forward + 4 backward threads moved the same total, each transfer cost a 1.00 fee
        BigDecimal expectedFees = new BigDecimal("160000.00");
        assertEquals(0, expectedFees.compareTo(bank.totalFees()));
        assertEquals(0, start.multiply(BigDecimal.valueOf(2)).compareTo(bank.totalBalance().add(bank.totalFees())));
    }

    @Test
    void concurrentDepositsOnOneAccountLoseNoUpdates() throws Exception {
        BankService bank = new BankService(new InMemoryAccountRepository(), new InMemoryTransactionLog(),
                SnapshotStore.noop());
        String acc = bank.openAccount(AccountType.SAVINGS, "Ann", "ann@test.com", "1234".toCharArray(),
                BigDecimal.ZERO).getNumber();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        java.util.List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            futures.add(pool.submit(() -> bank.deposit(acc, new BigDecimal("0.01"))));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertEquals(0, new BigDecimal("100.00").compareTo(bank.getBalance(acc)));
    }
}
