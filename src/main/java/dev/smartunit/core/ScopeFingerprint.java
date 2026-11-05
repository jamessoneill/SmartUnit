// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Global invalidation: Java runtime, class *additions/removals*, and resources/config.
 * Changed existing class bytes are intentionally NOT included here: they are per-test deps.
 * A scoped dependency hash is still a heuristic, not a proof of test independence.
 */
public final class ScopeFingerprint {
    private ScopeFingerprint() {}

    public static String calculate(Path projectDir) throws IOException {
        MessageDigest digest = Hashes.newDigest();
        add(digest, "smartunit-prototype-v2");
        String policy = System.getProperty("smartunit.policy", "observed");
        if (!policy.equals("observed") && !policy.equals("conservative"))
            throw new IOException("Unknown smartunit.policy: " + policy);
        add(digest, "policy:" + policy);
        add(digest, System.getProperty("java.runtime.version", "?"));
        add(digest, System.getProperty("os.name", "?"));
        add(digest, System.getProperty("os.arch", "?"));
        add(digest, System.getProperty("smartunit.context", "default"));
        add(digest, System.getProperty("smartunit.includes", ""));

        String dirs = System.getProperty("smartunit.classDirs",
                "target/classes,build/classes/java/main,build/classes/kotlin/main");
        boolean hasClasses = false;
        for (String rootText : dirs.split(",")) {
            Path root = projectDir.resolve(rootText.trim()).normalize();
            if (!Files.isDirectory(root)) continue;
            hasClasses = true;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : sorted(walk.filter(Files::isRegularFile).toList())) {
                    if (p.getFileName().toString().endsWith(".class")) {
                        // Only the CLASS NAMES: adding/removing types invalidates all tests.
                        add(digest, "class:" + root.relativize(p));
                        if (policy.equals("conservative")) add(digest, Hashes.ofFile(p));
                    } else {
                        addFile(digest, root, p);
                    }
                }
            }
        }
        if (!hasClasses) throw new IOException("No production class directory: cannot safely select tests");

        // Conservative policy also invalidates on changes to *any* compiled test helper.
        // It intentionally trades selection granularity for safer decisions.
        if (policy.equals("conservative")) {
            String testDirs = System.getProperty("smartunit.testClassDirs",
                    "target/test-classes,build/classes/java/test,build/classes/kotlin/test");
            for (String rootText : testDirs.split(",")) {
                if (rootText.isBlank()) continue;
                Path root = projectDir.resolve(rootText.trim()).normalize();
                if (!Files.isDirectory(root)) continue;
                try (Stream<Path> walk = Files.walk(root)) {
                    for (Path p : sorted(walk.filter(Files::isRegularFile).toList())) {
                        addFile(digest, root, p);
                    }
                }
            }
        }
        for (String relative : List.of("src/main/resources", "src/test/resources")) {
            Path root = projectDir.resolve(relative);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : sorted(walk.filter(Files::isRegularFile).toList())) {
                    addFile(digest, root, p);
                }
            }
        }
        // Conventional build metadata, plus user-declared config/env files.
        List<String> files = new ArrayList<>(List.of("pom.xml", "build.gradle",
                "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
                "gradle.lockfile", "gradle.properties"));
        String configured = System.getProperty("smartunit.scopeFiles", "");
        if (!configured.isBlank()) files.addAll(List.of(configured.split(",")));
        for (String file : files) {
            Path p = projectDir.resolve(file.trim()).normalize();
            add(digest, "file:" + file);
            if (!Files.exists(p)) { add(digest, "MISSING"); continue; }
            if (!Files.isRegularFile(p)) throw new IOException("Not a scope file: " + p);
            add(digest, Hashes.ofFile(p));
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static List<Path> sorted(List<Path> paths) {
        return paths.stream().sorted().toList();
    }
    private static void addFile(MessageDigest digest, Path root, Path file) throws IOException {
        add(digest, "resource:" + root.relativize(file));
        add(digest, Hashes.ofFile(file));
    }
    private static void add(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
