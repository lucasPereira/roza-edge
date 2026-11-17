# Róża Edge

Róża Edge is a framework for automatic test code refactoring.

## Current scope

The current work compares Spoon and OpenRewrite on the university system
subject, so the project is deliberately limited to `spikes/` and `subjects/`.
Do not create the engine, backend, frontend, or experiments structure until the
scope expands to require them.

## Spikes

The `spikes/` directory is a single Gradle project containing the candidate
technology implementations. Keep each spike in its own Java package so the
implementations remain separate without becoming independent Gradle projects.
Because the comparison must evaluate the technologies under the same
conditions, every spike must use the same subject and acceptance criteria.

## Subjects

The `subjects/` directory contains the controlled and real-world projects used
to evaluate the candidate implementations. Each subject is pinned as a Git
submodule so that every evaluation uses a known, reproducible revision. Subjects
remain outside the root Gradle build because they are evaluation inputs rather
than modules of Róża Edge itself.

## Development

Add only the code and dependencies required by the current task so the project
does not accumulate speculative structure. Use clear names and small, focused
types and methods, and prefer code that expresses its intent over explanatory
comments. When transforming a subject, preserve its compilation and test
behavior because a refactoring is valid only if the subject remains executable
and its behavior does not change.
