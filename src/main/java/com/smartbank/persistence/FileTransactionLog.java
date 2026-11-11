package com.smartbank.persistence;

import com.smartbank.exception.StorageException;
import com.smartbank.model.Transaction;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Append-only text log. Every entry is flushed immediately, so a crash loses at most the line being written.
 * Such a torn last line is skipped on read, and a missing trailing newline is repaired on open.
 */
public final class FileTransactionLog implements TransactionLog {
    private static final Logger LOG = Logger.getLogger(FileTransactionLog.class.getName());

    private final Path file;
    private final BufferedWriter writer;
    private final long maxIdAtOpen;

    public FileTransactionLog(Path file) {
        this.file = file;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.maxIdAtOpen = scanMaxId();
            repairMissingNewline();
            this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new StorageException("Cannot open transaction log " + file, e);
        }
    }

    @Override
    public synchronized void append(Transaction transaction) {
        try {
            writer.write(TransactionCodec.encode(transaction));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new StorageException("Cannot write to transaction log", e);
        }
    }

    @Override
    public synchronized List<Transaction> findByAccount(String accountNumber, int limit) {
        try {
            writer.flush();
            Deque<Transaction> window = new ArrayDeque<>(limit + 1);
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                lines.map(FileTransactionLog::tryDecode)
                        .flatMap(Optional::stream)
                        .filter(t -> t.accountNumber().equals(accountNumber))
                        .forEach(t -> {
                            window.addLast(t);
                            if (window.size() > limit) {
                                window.removeFirst();
                            }
                        });
            }
            List<Transaction> result = new ArrayList<>(window);
            Collections.reverse(result); // newest first
            return result;
        } catch (IOException | UncheckedIOException e) {
            throw new StorageException("Cannot read transaction log", e);
        }
    }

    @Override
    public long maxTransactionId() {
        return maxIdAtOpen;
    }

    @Override
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException e) {
            LOG.warning("Failed to close transaction log: " + e.getMessage());
        }
    }

    private long scanMaxId() throws IOException {
        if (!Files.exists(file)) {
            return 0;
        }
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.map(FileTransactionLog::tryDecode)
                    .flatMap(Optional::stream)
                    .mapToLong(Transaction::id)
                    .max()
                    .orElse(0);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** If a previous run crashed mid-line, make sure the next entry starts on a fresh line. */
    private void repairMissingNewline() throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            return;
        }
        try (SeekableByteChannel ch = Files.newByteChannel(file, StandardOpenOption.READ)) {
            ch.position(ch.size() - 1);
            ByteBuffer last = ByteBuffer.allocate(1);
            ch.read(last);
            if (last.get(0) != '\n') {
                Files.writeString(file, System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            }
        }
    }

    private static Optional<Transaction> tryDecode(String line) {
        if (line.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TransactionCodec.decode(line));
        } catch (StorageException e) {
            LOG.warning("Skipping unreadable transaction log line (possible crash during write)");
            return Optional.empty();
        }
    }
}
