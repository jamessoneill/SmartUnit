# Quick start

SmartUnit currently supports **JUnit Jupiter**, Java 17+, and an attached JaCoCo agent. This is an **experimental opt-in** workflow. Do not replace release-gate full-suite runs with selective testing.

## 1. Build the library

```bash
mvn install
```

Not on Maven Central yet. Add the local dependency in your application's `pom.xml`:

```xml
<dependency>
  <groupId>dev.smartunit</groupId>
  <artifactId>smartunit-junit5</artifactId>
  <version>0.2.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

Add the agent and keep its generated Surefire arguments:

```xml
<build><plugins>
  <plugin>
    <groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.16</version>
    <executions><execution><goals><goal>prepare-agent</goal></goals></execution></executions>
  </plugin>
  <plugin>
    <groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.2</version>
    <configuration>
      <argLine>@{argLine}</argLine>
      <forkCount>1</forkCount>
      <reuseForks>true</reuseForks>
      <systemPropertyVariables>
        <junit.jupiter.extensions.autodetection.enabled>true</junit.jupiter.extensions.autodetection.enabled>
        <smartunit.projectDir>${project.basedir}</smartunit.projectDir>
        <smartunit.cache>${project.basedir}/.smartunit/cache.tsv</smartunit.cache>
      </systemPropertyVariables>
    </configuration>
  </plugin>
</plugins></build>
```

If you already use a custom `argLine`, merge it with `@{argLine}`. JUnit is already supplied by your application; add `junit-jupiter` as a test dependency if necessary. See the complete [Maven demo](../examples/maven-demo/pom.xml).

## 2. Opt in

```java
@Tag("smartunit")
class MyServiceTest {
  @Test void calculates() { /* ... */ }
}
```

Supported: ordinary `@Test` methods without shared `@BeforeAll`, `@AfterAll`, `PER_CLASS` lifecycle or parallel JUnit execution. Parameterized, repeated, dynamic and other test engines are **not supported**; they run normally. Code that uses time, environment variables, sockets, DBs, other test state or reflection may be unsafe to skip.

## 3. Learn, inspect and select

```bash
mvn test -Dsmartunit.mode=record -Dsmartunit.includes=com.mycompany \
  -Dsmartunit.verbose=true

./scripts/inspect-cache.sh .smartunit/cache.tsv summary

# Audit decisions without skipping any tests.
mvn test -Dsmartunit.mode=audit -Dsmartunit.includes=com.mycompany

# Only after comparing with real full runs, consider selection.
mvn test -Dsmartunit.mode=select -Dsmartunit.includes=com.mycompany \
  -Dsmartunit.verbose=true
```

Use `-Dsmartunit.policy=conservative` to rerun the set when **any** compiled code changes. The policy is part of the global fingerprint, so the first run after switching policies trains a fresh fingerprint.

## 4. CI use

Restore `.smartunit/cache.tsv` from a previous successful run of the **same trusted branch and environment**. Ensure the run uses the same package, build metadata and `smartunit.context`. The cache is module-local; multi-module builds need individual caches. Do not share caches across untrusted pull requests or treat a restored cache as a proof of test success. Run full-suite validation jobs and compare their outcomes.

Manual full run:

```bash
mvn test -Dsmartunit.mode=off
```

## Troubleshooting

- No skips? Check `@Tag("smartunit")`, include prefix, attached JaCoCo agent, cache path, parallel execution, and `-Dsmartunit.verbose=true` output.
- `CACHE_MISS`: run in `record` first; passing tests only are saved.
- `SCOPE_CHANGED`: changes to resources, build files, class inventory, context or policy invalidated a previous recording.
- `CACHE_UNAVAILABLE`: inspect the flat file; do **not** edit fingerprints by hand. Back up/delete an invalid cache and train again.
- Package prefix mismatch: `com.acme` matches `com/acme/*` but not `com/acme2/*`.
