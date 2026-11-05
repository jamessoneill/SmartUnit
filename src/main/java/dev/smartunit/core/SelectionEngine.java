// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.core;

import java.io.IOException;
import java.util.Optional;

/** Explainable, fail-open decisions: when evidence is insufficient, run the test. */
public final class SelectionEngine {
    public enum Decision { RUN, SKIP }

    /** Machine-friendly reason and a human-readable explanation for logs and CI. */
    public record Result(Decision decision, String reason, String explanation) {
        public boolean skipped() { return decision == Decision.SKIP; }
    }

    private final CacheStore cache;
    private final ClassLoader loader;

    public SelectionEngine(CacheStore cache, ClassLoader loader) {
        this.cache = cache;
        this.loader = loader;
    }

    /** Compatibility convenience method. For diagnostics prefer {@link #explain}. */
    public Decision decide(String testId, String testClassName, String scopeHash) {
        return explain(testId, testClassName, scopeHash).decision();
    }

    public Result explain(String testId, String testClassName, String scopeHash) {
        try {
            Optional<CacheEntry> saved = cache.find(testId);
            if (saved.isEmpty()) return run("CACHE_MISS", "no successful recording for this test");
            CacheEntry entry = saved.get();
            if (scopeHash == null || !scopeHash.equals(entry.scopeHash()))
                return run("SCOPE_CHANGED", "global code inventory, configuration, or environment changed");
            String currentTestHash = Hashes.ofClass(loader, testClassName.replace('.', '/'));
            if (currentTestHash == null) return run("TEST_BYTECODE_MISSING", testClassName);
            if (!currentTestHash.equals(entry.testClassHash()))
                return run("TEST_CHANGED", testClassName);
            if (entry.dependencies().isEmpty())
                return run("EMPTY_COVERAGE", "recorded execution contains no attributable dependencies");
            for (var e : entry.dependencies().entrySet()) {
                String current = Hashes.ofClass(loader, e.getKey());
                if (current == null) return run("DEPENDENCY_MISSING", e.getKey().replace('/', '.'));
                if (!current.equals(e.getValue()))
                    return run("DEPENDENCY_CHANGED", e.getKey().replace('/', '.'));
            }
            return new Result(Decision.SKIP, "CACHE_HIT", "previous successful run; recorded inputs unchanged");
        } catch (IOException | RuntimeException e) {
            return run("CACHE_UNAVAILABLE", "unreadable cache or classpath: " + e.getClass().getSimpleName());
        }
    }

    private static Result run(String reason, String explanation) {
        return new Result(Decision.RUN, reason, explanation);
    }
}
