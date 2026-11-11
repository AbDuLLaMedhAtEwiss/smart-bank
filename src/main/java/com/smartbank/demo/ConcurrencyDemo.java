package com.smartbank.demo;

import com.smartbank.exception.InsufficientFundsException;
import com.smartbank.model.AccountType;
import com.smartbank.persistence.InMemoryTransactionLog;
import com.smartbank.persistence.SnapshotStore;
import com.smartbank.repository.InMemoryAccountRepository;
import com.smartbank.service.BankService;
import com.smartbank.util.Money;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Stress test that proves the locking is correct.
 *
 * <p>Many threads fire random transfers / deposits / withdrawals at a small set of accounts (so collisions and
 * opposite-direction transfers are constant). Afterwards the invariant
 * <pre>  sum(balances) + fees  ==  initial money + deposits - withdrawals</pre>
 * must hold exactly. Any lost update or half-applied transfer would break it.
 */
public final class ConcurrencyDemo {

    public record Result(boolean passed, boolean completedInTime, BigDecimal expectedTotal, BigDecimal actualTotal,
                         long elapsedMillis, int operations, int rejected, int unexpectedErrors) {
    }

    private ConcurrencyDemo() {
    }

    public static Result run(int accountCount, int threads, int operations, PrintStream out)
            throws InterruptedException {
        if (accountCount < 2 || threads < 1 || operations < 1) {
            throw new IllegalArgumentException("Need at least 2 accounts, 1 thread and 1 operation");
        }
        BankService bank = new BankService(new InMemoryAccountRepository(), new InMemoryTransactionLog(),
                SnapshotStore.noop());
        BigDecimal initial = new BigDecimal("10000.00");
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < accountCount; i++) {
            AccountType type = i % 2 == 0 ? AccountType.SAVINGS : AccountType.CURRENT;
            numbers.add(bank.openAccount(type, "Customer " + i, "customer" + i + "@demo.com",
                    "1234".toCharArray(), initial).getNumber());
        }
        out.printf("Created %d accounts with %s each. Running %,d operations on %d threads...%n",
                accountCount, Money.format(initial), operations, threads);

        LongAdder depositedCents = new LongAdder();
        LongAdder withdrawnCents = new LongAdder();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        Runnable task = () -> {
            try {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                int roll = rnd.nextInt(100);
                BigDecimal amount = BigDecimal.valueOf(rnd.nextLong(100, 50_000), 2); // 1.00 .. 499.99
                String a = numbers.get(rnd.nextInt(accountCount));
                if (roll < 70) {
                    String b;
                    do {
                        b = numbers.get(rnd.nextInt(accountCount));
                    } while (b.equals(a));
                    bank.transfer(a, b, amount);
                } else if (roll < 85) {
                    bank.deposit(a, amount);
                    depositedCents.add(amount.movePointRight(2).longValueExact());
                } else {
                    bank.withdraw(a, amount);
                    withdrawnCents.add(amount.movePointRight(2).longValueExact());
                }
            } catch (InsufficientFundsException e) {
                rejected.incrementAndGet(); // legitimate business outcome, balance was left untouched
            } catch (Throwable t) {
                unexpected.incrementAndGet();
            }
        };

        long start = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < operations; i++) {
            pool.execute(task);
        }
        pool.shutdown();
        boolean finished = pool.awaitTermination(2, TimeUnit.MINUTES);
        if (!finished) {
            pool.shutdownNow();
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        BigDecimal expected = initial.multiply(BigDecimal.valueOf(accountCount))
                .add(BigDecimal.valueOf(depositedCents.sum() - withdrawnCents.sum(), 2));
        BigDecimal actual = bank.totalBalance().add(bank.totalFees());
        boolean passed = finished && unexpected.get() == 0 && expected.compareTo(actual) == 0;

        out.printf("Finished in %,d ms (%,d ops/s)%n", elapsedMillis,
                elapsedMillis == 0 ? operations : operations * 1000L / elapsedMillis);
        out.printf("Rejected for insufficient funds : %,d%n", rejected.get());
        out.printf("Unexpected errors               : %d%n", unexpected.get());
        out.printf("Expected total (money + fees)   : %s%n", Money.format(expected));
        out.printf("Actual total                    : %s%n", Money.format(actual));
        out.println(passed ? "RESULT: PASS - no money created or lost, no deadlock."
                : "RESULT: FAIL - inconsistency or deadlock detected!");
        return new Result(passed, finished, expected, actual, elapsedMillis, operations,
                rejected.get(), unexpected.get());
    }
}
