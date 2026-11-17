# Róża Edge

Róża Edge is a framework for automatic test code refactoring.

The first development stage compares Spoon and OpenRewrite by implementing the
same small refactoring in two isolated Gradle subprojects:

- `spikes/spoon`
- `spikes/openrewrite`

Evaluation projects are pinned as Git submodules under `subjects/`.

## Requirements

- JDK 25

## Build

```sh
./gradlew check
```
