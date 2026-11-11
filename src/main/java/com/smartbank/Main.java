package com.smartbank;

import com.smartbank.cli.MenuController;
import com.smartbank.config.AppConfig;
import com.smartbank.service.BankService;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        AppConfig config = AppConfig.load();
        Files.createDirectories(config.dataDir());
        configureLogging(config);

        BankService bank = BankService.getInstance();
        // Also covers Ctrl+C: autosave + final snapshot + ledger close. close() is idempotent.
        Runtime.getRuntime().addShutdownHook(new Thread(bank::close, "bank-shutdown"));

        new MenuController(bank, config).run();
        bank.close();
    }

    /** Logs go to data/smartbank.log so they never interleave with the interactive menu. */
    private static void configureLogging(AppConfig config) throws IOException {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$-7s %3$s - %5$s%6$s%n");
        LogManager.getLogManager().reset();
        FileHandler handler = new FileHandler(config.appLogFile().toString(), true);
        handler.setFormatter(new SimpleFormatter());
        Logger root = Logger.getLogger("");
        root.addHandler(handler);
        root.setLevel(Level.INFO);
    }
}
