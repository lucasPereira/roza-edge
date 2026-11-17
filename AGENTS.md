# Róża Edge guidance

Róża Edge is a Java 25, Gradle multi-project framework for automatic test
code refactoring.

## Current scope

- Treat `spikes/spoon` and `spikes/openrewrite` as controlled technology
  experiments, not production engine modules.
- Evaluate both source-model technologies with the same subject and acceptance
  criteria.
- Do not introduce the production engine or commit to pipeline stages during
  the technology experiment.
- Keep projects under `subjects/` as pinned Git submodules outside the root
  Gradle build.

## Development

- Use the package prefix `br.dev.lucas.roza`.
- Add only code and dependencies required by the current task.
- Prefer clear names and small, focused types and methods.
- Do not add comments when the code can express the intent.
- Preserve compilation and test behavior when transforming a subject.
