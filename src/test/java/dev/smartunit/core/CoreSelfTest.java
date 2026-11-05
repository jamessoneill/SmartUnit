// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.core;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Dependency-free core verification; ./scripts/check-core.sh */
public final class CoreSelfTest {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("smartunit-selftest");
        Path cp = temp.resolve("cp");
        Files.createDirectories(cp.resolve("demo"));
        Path test = cp.resolve("demo/PayTest.class");
        Path dep = cp.resolve("demo/Payment.class");
        Files.writeString(test, "test-bytecode-v1", StandardCharsets.UTF_8);
        Files.writeString(dep, "dependency-bytecode-v1", StandardCharsets.UTF_8);
        String scope1 = Hashes.of("scope1");
        String scope2 = Hashes.of("scope2");

        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{cp.toUri().toURL()})) {
            CacheStore store = new CacheStore(temp.resolve(".smartunit/cache.tsv"));
            SelectionEngine engine = new SelectionEngine(store, loader);
            String id = "[engine:junit-jupiter]/[class:demo.PayTest]/[method:test()]";
            eq("CACHE_MISS", engine.explain(id, "demo.PayTest", scope1).reason());
            store.put(new CacheEntry(id, Hashes.ofFile(test), scope1,
                    Map.of("demo/Payment", Hashes.ofFile(dep))));
            eq(SelectionEngine.Decision.SKIP, engine.decide(id, "demo.PayTest", scope1));
            eq("CACHE_HIT", engine.explain(id, "demo.PayTest", scope1).reason());
            eq("SCOPE_CHANGED", engine.explain(id, "demo.PayTest", scope2).reason());
            Files.writeString(dep, "dependency-bytecode-v2");
            eq("DEPENDENCY_CHANGED", engine.explain(id, "demo.PayTest", scope1).reason());
            Files.delete(dep);
            eq("DEPENDENCY_MISSING", engine.explain(id, "demo.PayTest", scope1).reason());
            Files.writeString(dep, "dependency-bytecode-v1");
            Files.writeString(test, "test-bytecode-v2");
            eq("TEST_CHANGED", engine.explain(id, "demo.PayTest", scope1).reason());
            Files.writeString(test, "test-bytecode-v1");
            eq(1, store.entries().size());
            store.remove(id);
            eq(SelectionEngine.Decision.RUN, engine.decide(id, "demo.PayTest", scope1));
            Files.writeString(temp.resolve(".smartunit/cache.tsv"), "BROKEN");
            eq("CACHE_UNAVAILABLE", engine.explain(id, "demo.PayTest", scope1).reason());
            // A valid-looking cache with invalid hashes must also fail open.
            Files.writeString(temp.resolve(".smartunit/cache.tsv"), "# smartunit-cache-v1\naWRlbnRpZmllcg\tnot-a-hash\tnope\t\n");
            eq("CACHE_UNAVAILABLE", engine.explain(id, "demo.PayTest", scope1).reason());
        }
        Path project = temp.resolve("project");
        Path production = project.resolve("target/classes/demo");
        Path support = project.resolve("target/test-classes/demo");
        Files.createDirectories(production);
        Files.createDirectories(support);
        Path prodClass = production.resolve("Service.class");
        Path testHelper = support.resolve("Helper.class");
        Files.writeString(prodClass, "v1");
        Files.writeString(testHelper, "v1");
        try {
            System.setProperty("smartunit.policy", "observed");
            System.setProperty("smartunit.includes", "demo");
            String observed1 = ScopeFingerprint.calculate(project);
            Files.writeString(prodClass, "v2");
            eq(observed1, ScopeFingerprint.calculate(project));
            System.setProperty("smartunit.policy", "conservative");
            String conservative1 = ScopeFingerprint.calculate(project);
            Files.writeString(prodClass, "v3");
            ne(conservative1, ScopeFingerprint.calculate(project));
            String conservative2 = ScopeFingerprint.calculate(project);
            Files.writeString(testHelper, "v2");
            ne(conservative2, ScopeFingerprint.calculate(project));
            System.setProperty("smartunit.policy", "observed");
            Files.writeString(production.resolve("NewClass.class"), "v1");
            ne(observed1, ScopeFingerprint.calculate(project));
        } finally {
            System.clearProperty("smartunit.policy");
            System.clearProperty("smartunit.includes");
        }
        System.out.println("PASS: reason codes, cache integrity, bytecode invalidation, observed/conservative scope");
    }
    private static void eq(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + " got " + actual);
    }
    private static void ne(Object expected, Object actual) {
        if (expected.equals(actual)) throw new AssertionError("expected values to differ: " + actual);
    }
}
