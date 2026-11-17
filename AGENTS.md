# Róża Edge guidance

Róża Edge is a Java 25, Gradle multi-project framework for automatic test
code refactoring.

## Current scope

The `spikes/spoon` and `spikes/openrewrite` projects are controlled experiments
for comparing the two source-model technologies, not production engine modules.
Both experiments must use the same subject and acceptance criteria so that their
results remain comparable. Until this evaluation is complete, do not introduce
the production engine or commit Róża Edge to specific pipeline stages.

The `subjects/` directory contains the controlled and real-world projects used
as inputs in Róża Edge experiments. Each subject is pinned as a Git submodule
so that experiments run against a known, reproducible revision. Subjects remain
outside the root Gradle build because they are systems under transformation and
evaluation, not modules of Róża Edge itself.

## Development

- Add only code and dependencies required by the current task.
- Prefer clear names and small, focused types and methods.
- Do not add comments when the code can express the intent.
- Preserve compilation and test behavior when transforming a subject.
