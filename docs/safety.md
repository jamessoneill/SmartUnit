# Safety model: what SmartUnit can and cannot infer

SmartUnit performs **dynamic test impact analysis**. JaCoCo probe hits from a successful test are evidence that it executed classes; they are not proof that other classes cannot affect future behaviour. A cache hit is **not a passed test**. It is a decision to skip an eligible test using historical evidence.

## What changes invalidate a recording

- Recorded test class bytecode, touched/included class bytecode, production class **names**, project resources, conventional build files and `smartunit.scopeFiles`.
- The JDK/runtime/OS, `smartunit.includes`, `smartunit.context`, and selected policy.
- Under `conservative`, any compiled production or test-class bytecode change in the configured directories.
- Errors, missing information, failed or aborted tests, and unsupported JUnit cases lead to running tests.

## Risky dependencies

Unexecuted branches, reflection, service loaders, generated classes, dependency-injection wiring, external JARs, fixtures and static state can affect tests without appearing as probe hits. Also watch for class loaders, native code, environment variables, feature flags, DBs, network services, clock/random state, file inputs and asynchronous threads. JaCoCo's reset is **process global**; disabling JUnit parallelism alone does not prevent asynchronous contamination.

**Observed policy** can incorrectly skip a test when an unobserved dependency changes. **Conservative policy** is stronger for compiled project code but does not prove independence from external libraries or state. Both require ordinary full-suite runs.

## Conservative operation

1. Start with a small tagged group of isolated unit tests; do not tag external/integration tests. Use `audit` to observe predicted skips while still executing every test.
2. Pin dependencies or add dependency-lock checksum to `smartunit.context` and any additional input files to `smartunit.scopeFiles`.
3. Use branch-scoped trusted CI caches and include relevant configuration in your cache strategy.
4. Keep full-suite jobs running on protected branches and before releases; compare full vs selected failures for the same revision.
5. During rollout, consider `conservative` policy and observe logs for skipped-but-should-have-run tests.
6. If in doubt, set `smartunit.mode=off` or delete the cache.

## Security

The cache file is **untrusted input**. Its format is validated and parse errors force a run. Do not restore caches from untrusted forks/PRs. A malicious actor who can modify your build, dependency binaries and cache can undermine most build-selection systems. Never treat cache output as a security attestation.
