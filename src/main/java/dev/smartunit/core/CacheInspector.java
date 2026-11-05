// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.core;

import java.nio.file.Path;
import java.util.Comparator;

/** Zero-dependency command-line inspection of the SmartUnit flat-file cache. */
public final class CacheInspector {
    private CacheInspector() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3) {
            usage();
            System.exit(2);
        }
        String command = args.length >= 2 ? args[1] : "summary";
        var entries = new CacheStore(Path.of(args[0])).entries();
        switch (command) {
            case "summary" -> {
                int deps = entries.values().stream().mapToInt(e -> e.dependencies().size()).sum();
                System.out.println("Saved successful tests: " + entries.size());
                System.out.println("Recorded class dependencies: " + deps);
            }
            case "list" -> entries.keySet().stream().sorted().forEach(System.out::println);
            case "show" -> {
                if (args.length != 3) { usage(); System.exit(2); }
                CacheEntry e = entries.get(args[2]);
                if (e == null) { System.err.println("No cache entry for " + args[2]); System.exit(1); }
                else {
                    System.out.println("Test: " + e.testId());
                    System.out.println("Test class SHA-256: " + e.testClassHash());
                    System.out.println("Scope SHA-256: " + e.scopeHash());
                    e.dependencies().entrySet().stream().sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                            .forEach(dep -> System.out.println("  " + dep.getKey().replace('/', '.') + " " + dep.getValue()));
                }
            }
            default -> { usage(); System.exit(2); }
        }
    }

    private static void usage() {
        System.err.println("Usage: CacheInspector CACHE_PATH [summary | list | show TEST_ID]");
    }
}
