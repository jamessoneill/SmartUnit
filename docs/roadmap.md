# Roadmap

This is a priority list, not a delivery promise. Please discuss larger changes in an issue before starting a significant refactor.

## Good first contributions

- Add unit tests for malformed/duplicate cache entries and invalid policy values.
- Add a reproducible audit-mode comparison fixture that catches a false skip.
- Improve `CacheInspector` UX, test it on Windows paths and write usage examples.
- Add a Windows-friendly replacement for Bash-only helper scripts.
- Improve example coverage and error messages for unsupported test types.
- Document Gradle manual configuration and a real multi-module setup.

## High-priority engineering work

1. **End-to-end verification**: validate JUnit extension ordering, JaCoCo agent behavior, failure/abort invalidation, and add tests that mutate production bytecode between builds.
2. **Coverage attribution**: per-test isolation and detection of background activity, classloader and fork combinations.
3. **Performance**: cache scope calculation per fork and batch cache updates without losing concurrency correctness.
4. **Dependency completeness**: dependency JAR/resource fingerprints, test helpers, generated files and user-declared external state.
5. **First-party build plugins**: Maven and Gradle setup with consistent defaults and diagnostics.
6. **Auditability**: CI-friendly summary, stable machine-readable JSON reasons, cache format migration.
7. **Benchmarks**: compare full and selected run results on public Java projects; publish false-skip and overhead data.

## Experimental future ideas

- Method-level impact analysis, balanced against increased complexity and false-skip risk.
- Explainable diff-to-test graph and a `smartunit why <test>` command.
- Safe record/replay in isolated test JVMs for workloads with global or static state.

A false skip is a more serious bug than an unnecessary rerun. Prefer correctness over a headline skip percentage.
