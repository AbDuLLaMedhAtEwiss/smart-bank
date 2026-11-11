package com.smartbank.persistence;

import com.smartbank.exception.StorageException;
import com.smartbank.exception.ValidationException;
import com.smartbank.model.AccountSnapshot;
import com.smartbank.model.AccountType;
import com.smartbank.model.Customer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Text file with one account per line.
 *
 * <p><b>Crash-safe writes:</b> data goes to {@code accounts.dat.tmp}, is fsync'ed, and is then moved over the real
 * file with an atomic rename. A crash at any moment leaves either the complete old file or the complete new one,
 * never a half-written file.
 */
public final class FileSnapshotStore implements SnapshotStore {
    private static final String HEADER = "#SMARTBANK-ACCOUNTS v1";
    private static final int FIELDS = 9;

    private final Path file;

    public FileSnapshotStore(Path file) {
        this.file = file;
    }

    @Override
    public List<AccountSnapshot> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) {
                return List.of();
            }
            if (!HEADER.equals(header)) {
                throw new StorageException("Unsupported accounts file format: " + header);
            }
            List<AccountSnapshot> result = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    result.add(parse(line));
                }
            }
            return result;
        } catch (IOException e) {
            throw new StorageException("Cannot read accounts file " + file, e);
        }
    }

    @Override
    public void save(List<AccountSnapshot> snapshots) {
        Path absolute = file.toAbsolutePath();
        Path tmp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try {
            Files.createDirectories(absolute.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write(HEADER);
                writer.newLine();
                for (AccountSnapshot s : snapshots) {
                    writer.write(format(s));
                    writer.newLine();
                }
            }
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                channel.force(true); // make sure the bytes are on disk before the rename
            }
            try {
                Files.move(tmp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("Cannot write accounts file " + file, e);
        }
    }

    private static String format(AccountSnapshot s) {
        return String.join("|",
                s.type().name(),
                Long.toString(s.id()),
                s.owner().name(),
                s.owner().email(),
                s.pinHash(),
                s.balance().toPlainString(),
                s.parameter().toPlainString(),
                Boolean.toString(s.locked()),
                Long.toString(s.createdAt().toEpochMilli()));
    }

    private static AccountSnapshot parse(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length != FIELDS) {
            throw new StorageException("Corrupt account line: " + line);
        }
        try {
            return new AccountSnapshot(
                    AccountType.valueOf(p[0]),
                    Long.parseLong(p[1]),
                    new Customer(p[2], p[3]),
                    p[4],
                    new BigDecimal(p[5]),
                    new BigDecimal(p[6]),
                    Boolean.parseBoolean(p[7]),
                    Instant.ofEpochMilli(Long.parseLong(p[8])));
        } catch (IllegalArgumentException | ValidationException e) { // NumberFormatException is an IllegalArgumentException
            throw new StorageException("Corrupt account line: " + line, e);
        }
    }
}
