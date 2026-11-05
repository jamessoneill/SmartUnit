// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.core;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** One text TSV flat file, with an OS lock for concurrent writer JVMs. */
public final class CacheStore {
    private static final String HEADER = "# smartunit-cache-v1";
    private final Path file;

    public CacheStore(Path file) { this.file = file; }

    public Optional<CacheEntry> find(String id) throws IOException {
        return Optional.ofNullable(readAll().get(id));
    }

    /** Snapshot for local inspection and reporting. */
    public Map<String, CacheEntry> entries() throws IOException {
        return Map.copyOf(readAll());
    }

    public void put(CacheEntry entry) throws IOException {
        modify(map -> map.put(entry.testId(), entry));
    }

    public void remove(String id) throws IOException {
        modify(map -> map.remove(id));
    }

    private void modify(java.util.function.Consumer<Map<String, CacheEntry>> change) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path lockPath = parent.resolve(file.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            Map<String, CacheEntry> all = readAll();
            change.accept(all);
            List<String> lines = new ArrayList<>();
            lines.add(HEADER);
            for (CacheEntry entry : all.values()) lines.add(encode(entry));
            Path tmp = Files.createTempFile(parent, "smartunit", ".tmp");
            try {
                Files.write(tmp, lines, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(tmp); }
        }
    }

    private Map<String, CacheEntry> readAll() throws IOException {
        Map<String, CacheEntry> map = new TreeMap<>();
        if (!Files.exists(file)) return map;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals(HEADER))
            throw new IOException("Unsupported or corrupt SmartUnit cache header");
        for (int i = 1; i < lines.size(); ++i) {
            try {
                CacheEntry entry = decode(lines.get(i));
                if (map.put(entry.testId(), entry) != null)
                    throw new IllegalArgumentException("duplicate test id");
            } catch (RuntimeException e) {
                throw new IOException("Corrupt SmartUnit cache line " + (i + 1), e);
            }
        }
        return map;
    }

    private static String encode(CacheEntry entry) {
        StringBuilder deps = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(entry.dependencies()).entrySet()) {
            if (!deps.isEmpty()) deps.append(',');
            deps.append(e.getKey()).append(':').append(e.getValue());
        }
        return b64(entry.testId()) + "\t" + entry.testClassHash() + "\t"
                + entry.scopeHash() + "\t" + b64(deps.toString());
    }

    private static CacheEntry decode(String line) {
        String[] cols = line.split("\t", -1);
        if (cols.length != 4) throw new IllegalArgumentException("expected 4 columns");
        Map<String, String> deps = new TreeMap<>();
        String raw = from64(cols[3]);
        if (!raw.isEmpty()) for (String item : raw.split(",")) {
            int pos = item.lastIndexOf(':');
            if (pos < 1) throw new IllegalArgumentException("bad dependency");
            deps.put(item.substring(0, pos), item.substring(pos + 1));
        }
        if (!isSha256(cols[1]) || !isSha256(cols[2]) || deps.isEmpty())
            throw new IllegalArgumentException("invalid fingerprint or empty coverage");
        for (var e : deps.entrySet()) {
            if (e.getKey().isEmpty() || !isSha256(e.getValue()))
                throw new IllegalArgumentException("invalid dependency fingerprint");
        }
        String id = from64(cols[0]);
        if (id.isBlank()) throw new IllegalArgumentException("missing test id");
        return new CacheEntry(id, cols[1], cols[2], deps);
    }

    private static boolean isSha256(String value) {
        return value.matches("[a-f0-9]{64}");
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String from64(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
