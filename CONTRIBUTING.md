# Contributing to SmartUnit

Thank you for helping build a reliable, understandable JUnit impact-selection tool. Reports of incorrect skips are particularly valuable.

## Start here

- Read the [README](README.md), [architecture](docs/architecture.md) and [safety model](docs/safety.md).
- Check the [roadmap](docs/roadmap.md) or open an issue describing the desired behaviour.
- For a first contribution, focus on an example, test, documentation or a specific reproducible bug.
- For larger work, propose an approach first so we can align on the invalidation and testing strategy.

## Development environment

JDK 17+ and Maven are needed for full verification. Core logic can be tested without Maven/JUnit:

```bash
./scripts/check-core.sh
mvn -B -ntp clean verify
./scripts/check-integration.sh
```

The last script builds/installs the library locally, runs the example in `record` and `select` modes, and checks verbose decisions. If Maven is unavailable, say which checks you could run in your PR.

## Pull request expectations

- Include a reproduction and core/integration test for changed selection behaviour.
- Make cache errors **run** tests; never turn unknown information into a skip.
- Update docs when adding a configuration property or changing semantics.
- Keep new dependencies minimal, document licensing, and don't commit generated `target`, `.smartunit`, or `.gradle` files.
- Use the Apache-2.0 SPDX header on new source files.
- Explain in the PR if any changes could increase the risk of incorrectly skipped tests.

Contributions are accepted under the repository's Apache License 2.0. By submitting you affirm you have the rights to contribute your code. Please follow the [Code of Conduct](CODE_OF_CONDUCT.md); report security vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
