package com.smartbank.observer;

import com.smartbank.model.Transaction;
import com.smartbank.util.Money;
import java.util.logging.Logger;

/** Simulated SMS/e-mail alerts (written to the application log). */
public final class NotificationService implements TransactionListener {
    private static final Logger LOG = Logger.getLogger(NotificationService.class.getName());

    @Override
    public void onTransaction(Transaction t) {
        LOG.info(() -> "[ALERT] %s on %s: %s (balance %s)".formatted(
                t.type(), t.accountNumber(), Money.format(t.amount()), Money.format(t.balanceAfter())));
    }
}
