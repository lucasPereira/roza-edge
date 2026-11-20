# Róża Edge

Róża Edge is the new implementation of the test code refactoring framework
developed for Lucas's doctorate.

## Commands

### Tests

```sh
./gradlew test # Runs tests in all modules.
./gradlew :spikes:test # Runs the spike tests.
```

### Checks

```sh
./gradlew check # Runs all module checks and Markdown linting.
./gradlew :spikes:check # Runs tests and checks Java formatting in spikes.
```

### Markdown linting

```sh
./gradlew markdownLint # Checks Markdown formatting only.
```
