# SmartUnit

**Run the tests that matter. Explain the ones you skip.**

SmartUnit is an **experimental**, opt-in JUnit Jupiter extension that learns which project classes a passing test executed, fingerprints their bytecode, and reuses that result only when recorded inputs still match. It stores its state in a readable-by-tools, local **TSV flat file**—no server or database required.

> **Safety status: research preview (0.2.0-SNAPSHOT).** Dynamic execution coverage is **not** a sound dependency graph. SmartUnit can miss dependencies via reflection, unexecuted branches, classpath/environment changes and background work. **Do not use selected runs as your only release or merge gate.** Run and compare full-suite jobs on protected branches. The JUnit/JaCoCo integration has not been executed end to end in the packaging environment; the repository CI is configured to do so when published.

## Why try SmartUnit?

- **Understand your build:** `-Dsmartunit.verbose=true` prints `CACHE_HIT`, `DEPENDENCY_CHANGED`, `SCOPE_CHANGED`, and other reasons.
- **Start small:** no changes to ordinary tests. Only `@Tag("smartunit")` on plain `@Test` methods qualifies.
- **No infrastructure:** per-module `.smartunit/cache.tsv`, portable between CI runs of the same trusted branch.
- **Control the risk:** `off`, `record`, `audit`, and `select` modes; `observed` and `conservative` invalidation policies; fail-open behaviour on uncertainty.
- **Easy to contribute:** dependency-free core self-tests, a Maven demo, CI, an architecture guide, a roadmap and issue templates.

## Try it in a Maven project

Requires JDK 17+ and Maven. From this repository:

```bash
mvn -B -ntp install

# Record successful tests in the included Maven example.
mvn -f examples/maven-demo/pom.xml clean test \
  -Dsmartunit.mode=record \
  -Dsmartunit.includes=dev.smartunit.demo \
  -Dsmartunit.verbose=true

# Run again without clean: unchanged eligible tests may now skip.
mvn -f examples/maven-demo/pom.xml test \
  -Dsmartunit.mode=select \
  -Dsmartunit.includes=dev.smartunit.demo \
  -Dsmartunit.verbose=true
```

Expected **illustrative** verbose output (actual identifiers depend on JUnit):

```text
[SmartUnit] RUN ... | CACHE_MISS | no successful recording for this test
[SmartUnit] RECORDED ... | PASS | 2 class dependencies
[SmartUnit] SKIP ... | CACHE_HIT | previous successful run; recorded inputs unchanged
```

Run one command to exercise the whole Maven demo (including audit and code-change invalidation) automatically:

```bash
./scripts/check-integration.sh
```

### Integrating another Maven repository

1. Add `dev.smartunit:smartunit-junit5:0.2.0-SNAPSHOT` as a `test` dependency, available after the local `mvn install` above. **It is not yet published to Maven Central.**
2. Add `jacoco-maven-plugin` and its `prepare-agent` goal to the project's build.
3. Configure `maven-surefire-plugin` with JaCoCo's `@{argLine}` and `junit.jupiter.extensions.autodetection.enabled=true`.
4. Tag suitable plain tests with `@Tag("smartunit")`; pass a **non-empty** `smartunit.includes` package prefix.
5. Run in `record` mode before `select`; persist `.smartunit/cache.tsv` only between runs from the same trusted context.

The [working Maven example](examples/maven-demo/pom.xml) contains the complete JaCoCo/Surefire setup. For full instructions see [Quick start](docs/quickstart.md).

## Understand selections

```bash
# Safe rollout: report which tests WOULD skip, but still execute them.
mvn test -Dsmartunit.mode=audit -Dsmartunit.includes=com.example

# Then opt into actual selection, with reasons.
mvn test -Dsmartunit.mode=select -Dsmartunit.includes=com.example \
  -Dsmartunit.verbose=true

# Inspect the cache without Maven or extra dependencies.
./scripts/inspect-cache.sh .smartunit/cache.tsv summary
./scripts/inspect-cache.sh .smartunit/cache.tsv list
./scripts/inspect-cache.sh .smartunit/cache.tsv show 'FULL_JUNIT_UNIQUE_ID'
```

Examples of reason codes:

| Reason | Meaning |
| --- | --- |
| `CACHE_HIT` | Saved successful result and recorded inputs match; skip is permitted |
| `CACHE_MISS` | Test has no saved successful result |
| `TEST_CHANGED` | The test class bytecode changed |
| `DEPENDENCY_CHANGED` | A previously executed class has changed |
| `SCOPE_CHANGED` | Class inventory, resources, build configuration or policy changed |
| `CACHE_UNAVAILABLE` | Cache file cannot be trusted; run the test |

## Policy and settings

| JVM property | Default | Purpose |
| --- | --- | --- |
| `smartunit.mode` | `off` | `off` = ordinary run, `record` = train, `audit` = predict but execute, `select` = potentially skip |
| `smartunit.policy` | `observed` | `observed` = per-test executed classes; `conservative` = also fingerprint all compiled production/test classes |
| `smartunit.includes` | empty | Comma-separated package prefixes, e.g. `com.acme.api,com.acme.data` |
| `smartunit.verbose` | `false` | Per-test selection and recording diagnostics |
| `smartunit.cache` | `.smartunit/cache.tsv` | Flat-file path |
| `smartunit.projectDir` | `.` | Module root for fingerprints |
| `smartunit.classDirs` | Maven/Gradle defaults | Compiled production class directories |
| `smartunit.testClassDirs` | Maven/Gradle defaults | Compiled test class directories in conservative policy |
| `smartunit.scopeFiles` | empty | Comma-separated additional files to hash |
| `smartunit.context` | `default` | User-provided environment/dependency lock fingerprint |

`audit` mode always runs eligible tests and refreshes passing fingerprints, while printing `WOULD_SKIP` or `WOULD_RUN`. It is the recommended first step after recording.

**Observed mode** can skip tests whose previously executed classes haven't changed, even if unrelated project classes did. That is useful, but **not guaranteed correct**. **Conservative mode** invalidates all recorded tests if *any* compiled production or test class changes. It provides fewer skips and still requires full-suite validation for external state. New/deleted production class names invalidate both modes.

## Help improve SmartUnit

If you are new to the project, start with an issue marked *good first issue* (once labels are configured) or a roadmap item. Contributions that strengthen correctness, make skipped tests easier to audit, and reproduce difficult build-tool configurations are particularly valuable.

- [Contributing](CONTRIBUTING.md) · [Architecture](docs/architecture.md) · [Safety and limitations](docs/safety.md)
- [Roadmap](docs/roadmap.md) · [Benchmark methodology](docs/benchmarks.md) · [Changelog](CHANGELOG.md)
- [Security policy](SECURITY.md) · [Code of conduct](CODE_OF_CONDUCT.md)

### Current verification

`./scripts/check-core.sh` **passed locally** with Java 21, without Maven. It exercises cache reason codes, invalidation, corrupt-cache handling, class additions, and both policies. **Maven was unavailable in the packaging environment**, so the end-to-end JUnit/JaCoCo demo and CI configuration remain unverified until run on a machine with Maven. We are not claiming measured pipeline speedups or production correctness.

**License:** [Apache License, Version 2.0](LICENSE) — Copyright 2026 SmartUnit contributors. Name/group coordinates are placeholders until maintainers finalize them.
