// Copyright 2026 SmartUnit contributors
// SPDX-License-Identifier: Apache-2.0

package dev.smartunit.junit;

import dev.smartunit.core.CacheEntry;
import dev.smartunit.core.CacheStore;
import dev.smartunit.core.Hashes;
import dev.smartunit.core.ScopeFingerprint;
import dev.smartunit.core.SelectionEngine;
import org.jacoco.agent.rt.RT;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.tools.ExecFileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Experimental JUnit Jupiter adapter.
 * Explicitly opt in with @Tag("smartunit") and -Dsmartunit.mode=record|select.
 * This is NOT a sound whole-program dependency analysis; see README safety notes.
 */
public final class SmartUnitExtension implements ExecutionCondition,
        BeforeEachCallback, AfterEachCallback, TestWatcher {
    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(SmartUnitExtension.class);
    private static final String PENDING = "pending-entry";

    private static String mode() { return System.getProperty("smartunit.mode", "off"); }
    private static boolean active() {
        return "record".equals(mode()) || "select".equals(mode()) || "audit".equals(mode());
    }
    private static void report(ExtensionContext ctx, String action, String reason, String detail) {
        if ("audit".equals(mode()) || Boolean.parseBoolean(System.getProperty("smartunit.verbose", "false"))) {
            System.out.printf("[SmartUnit] %s %s | %s | %s%n",
                    action, ctx.getUniqueId(), reason, detail);
        }
    }
    private static boolean included(String className) {
        String includes = System.getProperty("smartunit.includes", "");
        for (String text : includes.split(",")) {
            String prefix = text.trim().replace('.', '/').replaceAll("/+$", "");
            if (!prefix.isEmpty() && (className.equals(prefix) || className.startsWith(prefix + "/")))
                return true;
        }
        return false;
    }
    private static boolean eligible(ExtensionContext context) {
        // Restrict v0 to ordinary @Test only: no parameterized/dynamic/repeated test.
        return context.getTestMethod().map(m -> m.isAnnotationPresent(Test.class)).orElse(false)
                && context.getTags().contains("smartunit")
                && !hasSharedLifecycle(context.getRequiredTestClass());
    }
    // Pre-test setup executed once per class cannot be reliably attributed to a test.
    private static boolean hasSharedLifecycle(Class<?> type) {
        // Nested tests can depend on outer-instance lifecycle and setup.
        if (type.getEnclosingClass() != null) return true;
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            TestInstance instance = c.getAnnotation(TestInstance.class);
            if (instance != null && instance.value() == TestInstance.Lifecycle.PER_CLASS) return true;
            for (Method method : c.getDeclaredMethods()) {
                if (method.isAnnotationPresent(BeforeAll.class)
                        || method.isAnnotationPresent(AfterAll.class)) return true;
            }
        }
        return false;
    }
    private static boolean parallel(ExtensionContext ctx) {
        return Boolean.parseBoolean(ctx.getConfigurationParameter(
                "junit.jupiter.execution.parallel.enabled").orElse("false"));
    }
    private static boolean agent() {
        try { RT.getAgent(); return true; }
        catch (IllegalStateException | NoClassDefFoundError ex) { return false; }
    }
    private static CacheStore cache() {
        return new CacheStore(Path.of(System.getProperty("smartunit.cache", ".smartunit/cache.tsv")));
    }
    private static String scope() throws Exception {
        return ScopeFingerprint.calculate(Path.of(System.getProperty("smartunit.projectDir", ".")));
    }
    private static ClassLoader loader(ExtensionContext ctx) {
        return ctx.getRequiredTestClass().getClassLoader();
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext ctx) {
        if (!"select".equals(mode()) && !"audit".equals(mode()))
            return ConditionEvaluationResult.enabled("SmartUnit selection is off");
        if (!eligible(ctx))
            return ConditionEvaluationResult.enabled("SmartUnit: test not opted in or unsupported lifecycle");
        if (parallel(ctx)) {
            report(ctx, "audit".equals(mode()) ? "WOULD_RUN" : "RUN",
                    "PARALLEL_EXECUTION", "per-test JaCoCo capture is not isolated");
            return ConditionEvaluationResult.enabled("SmartUnit: parallel execution");
        }
        if (!agent()) {
            report(ctx, "audit".equals(mode()) ? "WOULD_RUN" : "RUN",
                    "NO_JACOCO_AGENT", "a coverage agent is required");
            return ConditionEvaluationResult.enabled("SmartUnit: JaCoCo agent is not attached");
        }
        try {
            SelectionEngine.Result selection = new SelectionEngine(cache(), loader(ctx))
                    .explain(ctx.getUniqueId(), ctx.getRequiredTestClass().getName(), scope());
            String action = "audit".equals(mode())
                    ? (selection.skipped() ? "WOULD_SKIP" : "WOULD_RUN")
                    : selection.decision().name();
            report(ctx, action, selection.reason(), selection.explanation());
            if ("select".equals(mode()) && selection.skipped())
                return ConditionEvaluationResult.disabled("SmartUnit " + selection.reason()
                        + ": " + selection.explanation());
            return ConditionEvaluationResult.enabled("SmartUnit " + selection.reason());
        } catch (Exception ex) {
            report(ctx, "audit".equals(mode()) ? "WOULD_RUN" : "RUN",
                    "FINGERPRINT_UNAVAILABLE", ex.getClass().getSimpleName());
            // Fail open. The JUnit test must run if the cache cannot be validated.
            return ConditionEvaluationResult.enabled("SmartUnit: no reusable fingerprint");
        }
    }

    @Override
    public void beforeEach(ExtensionContext ctx) {
        if (eligible(ctx) && !parallel(ctx) && agent() && active()) {
            RT.getAgent().reset();
        }
    }

    @Override
    public void afterEach(ExtensionContext ctx) {
        if (!eligible(ctx) || parallel(ctx) || !agent() || !active()) return;
        try {
            byte[] exec = RT.getAgent().getExecutionData(true);
            ExecFileLoader jacoco = new ExecFileLoader();
            jacoco.load(new ByteArrayInputStream(exec));
            if (System.getProperty("smartunit.includes", "").isBlank()) return;
            Map<String, String> deps = new TreeMap<>();
            for (ExecutionData data : jacoco.getExecutionDataStore().getContents()) {
                boolean touched = false;
                for (boolean probe : data.getProbes()) {
                    if (probe) { touched = true; break; }
                }
                if (!touched || !included(data.getName())) continue;
                String hash = Hashes.ofClass(loader(ctx), data.getName());
                if (hash == null) return; // Missing bytecode: don't record incomplete data.
                deps.put(data.getName(), hash);
            }
            if (deps.isEmpty()) return; // No known deps; cannot justify a skip.
            String testClassHash = Hashes.ofClass(loader(ctx),
                    ctx.getRequiredTestClass().getName().replace('.', '/'));
            if (testClassHash == null) return;
            ctx.getStore(NS).put(PENDING,
                    new CacheEntry(ctx.getUniqueId(), testClassHash, scope(), deps));
        } catch (Exception ignored) {
            // Never let instrumentation/cache errors fail an actual JUnit test.
        }
    }

    @Override
    public void testSuccessful(ExtensionContext ctx) {
        CacheEntry pending = ctx.getStore(NS).remove(PENDING, CacheEntry.class);
        if (pending == null) { invalidate(ctx); return; }
        try {
            cache().put(pending);
            report(ctx, "RECORDED", "PASS", pending.dependencies().size() + " class dependencies");
        } catch (Exception ex) {
            report(ctx, "NOT_RECORDED", "CACHE_WRITE_ERROR", ex.getClass().getSimpleName());
        }
    }
    @Override
    public void testFailed(ExtensionContext ctx, Throwable cause) { invalidate(ctx); }
    @Override
    public void testAborted(ExtensionContext ctx, Throwable cause) { invalidate(ctx); }

    private static void invalidate(ExtensionContext ctx) {
        if (!eligible(ctx)) return;
        report(ctx, "INVALIDATED", "TEST_FAILED_OR_ABORTED", "previous success is no longer reusable");
        try { cache().remove(ctx.getUniqueId()); }
        catch (Exception ignored) { /* corrupt/unwritable cache is treated as a miss */ }
    }
}
