# Róża Edge

Róża Edge is a framework for automatic test code refactoring.

The first development stage compares Spoon and OpenRewrite by applying the same
test refactoring to the university system subject.

## Structure

- `spikes` contains candidate technology implementations.
- `subjects` contains pinned projects used to evaluate those implementations and
  remains outside the root Gradle build.

## Requirements

- JDK 25

## Build

```sh
./gradlew check
```
