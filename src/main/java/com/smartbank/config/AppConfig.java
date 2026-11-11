package com.smartbank.config;

import java.nio.file.Path;
import java.time.Duration;

/** Runtime settings, overridable with -D system properties (e.g. -Dsmartbank.data.dir=/tmp/bank). */
public record AppConfig(Path dataDir, Duration autosaveInterval, Duration interestInterval, String adminPassword) {

    public static AppConfig load() {
        return new AppConfig(
                Path.of(System.getProperty("smartbank.data.dir", "data")),
                Duration.ofSeconds(Long.getLong("smartbank.autosave.seconds", 30L)),
                Duration.ofSeconds(Long.getLong("smartbank.interest.seconds", 30L * 24 * 3600)),
                System.getProperty("smartbank.admin.password", "admin123"));
    }

    public Path accountsFile() {
        return dataDir.resolve("accounts.dat");
    }

    public Path transactionsFile() {
        return dataDir.resolve("transactions.log");
    }

    public Path appLogFile() {
        return dataDir.resolve("smartbank.log");
    }
}
