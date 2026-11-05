# Architecture and contributor map

The execution model is intentionally small. It does **not** attempt whole-program static analysis.

```text
             JUnit Jupiter Extension
                      |
          +-----------+------------+
          |                        |
      Before/after              Before select
    JaCoCo execution               |
          |                 ScopeFingerprint
     Hashes / CacheEntry            |
          |                   SelectionEngine
       CacheStore                    |
       cache.tsv              RUN / SKIP + reason
```

## Main components

| Component | Responsibility | Test it with |
| --- | --- | --- |
| `SmartUnitExtension` | JUnit eligibility, JaCoCo reset/collection, record and skip callbacks | Maven demo and targeted integration tests |
| `ScopeFingerprint` | Global invalidations for configuration, resources, class additions and conservative policy | `CoreSelfTest` |
| `Hashes` | SHA-256 of bytes, files and class-loader resources | `CoreSelfTest` |
| `SelectionEngine` | Fail-open decision plus reason codes | `CoreSelfTest` |
| `CacheStore` | Versioned TSV, write lock, atomic replace, corrupt-file detection | `CoreSelfTest` |
| `CacheInspector` | Read-only command line summary/list/show | `scripts/inspect-cache.sh` |

## Storage schema (v1)

One text record per previously passing JUnit ID:

```text
# smartunit-cache-v1
BASE64URL(TEST_ID)\tSHA256(TEST_CLASS)\tSHA256(SCOPE)\tBASE64URL(COMMA_SEPARATED_CLASS:SHA256)
```

Write to a temporary file, acquire a lock, and atomically replace the file where supported. Readers treat invalid files as misses; they do not silently overwrite corrupt or incompatible caches.

## Decision algorithm

1. If the feature is off, the test is not opted in, JUnit is parallel, or the JaCoCo agent is missing: run normally. In `audit` mode, calculate the same decision, print `WOULD_SKIP`/`WOULD_RUN`, then always execute.
2. If the cache is absent, unreadable or not from a passing test: run.
3. If the global scope, class inventory, test bytecode, or recorded executed class hashes change: run.
4. Otherwise mark a test as **JUnit skipped** with a reason; never present it as a newly passed test.
5. On passing runs, record coverage. On failed/aborted runs, invalidate any previous successful entry.

## Known scale limitation

Each selected test currently scans the configured scope and reads the TSV file; each passing eligible test atomically rewrites the file. This is simple and crash-resistant but may cost time on large suites. A future version should snapshot the immutable build inventory once per test JVM and batch writes safely, with locking and invalidation tests. **Benchmark selection overhead before claiming speedups.**

## Extension points

- Input collectors: class files, JAR dependency locks, resources, test fixtures, environment fingerprints.
- Selection policies: conservative vs observed, future explicit dependency graph policy.
- Reporting: console reasons, later structured JSON and CI annotations.
- Build tooling: Maven and Gradle integrations that configure agent and cache automatically.

Contributions that increase the chance of skipping a test must include a counterexample test where **new or changed relevant code invalidates the cache**. Never fix an issue by making cache errors mean `SKIP`.
