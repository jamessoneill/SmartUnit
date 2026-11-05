// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Deterministic SHA-256 fingerprints of bytecode, resources and configuration. */
public final class Hashes {
    private Hashes() {}

    public static MessageDigest newDigest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String of(byte[] bytes) {
        return HexFormat.of().formatHex(newDigest().digest(bytes));
    }

    public static String of(String text) {
        return of(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String ofFile(Path file) throws IOException {
        return of(Files.readAllBytes(file));
    }

    /** Returns null if the class resource is unavailable (meaning: no safe cache hit). */
    public static String ofClass(ClassLoader loader, String internalName) throws IOException {
        String resource = internalName + ".class";
        try (InputStream in = loader.getResourceAsStream(resource)) {
            return in == null ? null : of(in.readAllBytes());
        }
    }
}
