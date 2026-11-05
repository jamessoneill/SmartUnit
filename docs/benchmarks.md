# Benchmark protocol

No speedup or correctness measurements have been published for SmartUnit yet. This file defines how we should measure them before promoting the tool.

For a pinned revision on a public, reproducible repository:

1. Warm dependencies equally for baseline and SmartUnit runs.
2. Collect full test-suite runtime and per-test outcomes, then record a full SmartUnit training run.
3. Measure a repeat selected run with no code changes (best case).
4. Make several small, realistic changes: changed method body, added class, changed resource, changed dependency JAR, and changed test helper.
5. For each change compare a full-suite run **on the exact same revision** against selected outcomes. Report false skips separately from unnecessary reruns.
6. Run each case at least five times on the same machine/environment; report medians, dispersion and configuration.

Recommended metrics:

| Metric | Definition |
| --- | --- |
| Total duration | Wall-clock CI job duration, including build & selection overhead |
| Test duration | Time spent by the test engine only |
| Selected/run/skipped count | Distinguish JUnit skipped tests from newly passing tests |
| False skips | Tests skipped in selection but failed or changed outcome in full run |
| Unnecessary reruns | Tests rerun despite no relevant changes (approximate by manual review) |
| Cache size / IO | Bytes and time for cache file read/write |
| Invalidation explainability | Share of decisions with a specific reason code |

Never claim "100% safe" based on a finite benchmark. Publish configuration, commit SHA and scripts so others can reproduce the results.
