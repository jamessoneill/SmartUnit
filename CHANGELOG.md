# Changelog

## 0.2.0-SNAPSHOT — unreleased

- Added explainable `RUN`/`SKIP` reason codes, optional verbose logging, and a no-skip `audit` rollout mode.
- Added a no-dependency flat-file cache inspector.
- Added `observed` and `conservative` fingerprint policies, package-prefix filtering and more corrupt-cache validation.
- Expanded dependency-free core verification; added a Maven/JUnit/JaCoCo demo and CI smoke-test workflow.
- Added contributor onboarding, issue/PR templates, security/safety notes, roadmap and benchmark methodology.
- Retained Apache License 2.0.

## 0.1.0-SNAPSHOT — experimental initial prototype

- Per-test JaCoCo probe collection and SHA-256 bytecode fingerprints.
- Local versioned TSV cache with atomic replacement and locking.
- JUnit Jupiter extension with opt-in selection and fail-open fallback.
