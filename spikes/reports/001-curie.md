# Group university tests

[Marie Curie](https://oumnh.ox.ac.uk/learn-marie-curie) (1867–1934) was a
Polish-born physicist and chemist who worked in France and studied
radioactivity. She was the first woman to receive a Nobel Prize and the first
person to receive two.

Her main contributions include **investigating radioactivity** through
experiments, building on Henri Becquerel's discovery, and **discovering polonium
and radium** with Pierre Curie. During World War I, she also **developed mobile
X-ray units** with her daughter Irène and trained volunteers to use them to help
wounded soldiers.

## Context

Róża Edge needs a source transformation tool that can refactor test code while
keeping the subject compilable and its tests executable. This spike asks whether
Spoon and OpenRewrite can group two tests with duplicated setup into a single
class, extract the setup into `@BeforeEach`, and retain both test scenarios.
Both implementations use the same controlled subject and acceptance criteria.
The scope is this known pair of tests; discovering duplication in arbitrary
projects remains outside this investigation.

## Subject

The subject is the [university system](../../subjects/university-system).

`CancelStudentEnrollmentTest` and `EnrollStudentTwiceTest` each contain one
JUnit test. The tests duplicate the same six setup statements: they create UFSC,
admit Alice, hire Bob, create the Computer Science program and Algorithms
course, and enroll Alice. Their actions and assertions differ: one test cancels
the enrollment; the other rejects a second enrollment. This duplicated setup
makes them a controlled case for comparing how Spoon and OpenRewrite extract
shared setup while preserving both test scenarios.

## Procedure

The expected output is `UniversityTest`, with both test methods, one
`@BeforeEach` method, and private fields for `alice` and `algorithms`.

Both candidates belong to `br.dev.lucas.roza.spikes.curie`, under the `spoon`
and `openrewrite` packages. The shared `UniversityRefactoringAcceptanceTest`
belongs directly to the spike's package. The dependencies are Spoon 11.5.0 and
OpenRewrite 8.90.4.

Spoon parses the two test sources with Java 25 compliance and no-classpath mode.
It compares the setup statements structurally, copies the second test method
into the first class, promotes the setup variables used by the test bodies to
fields, and creates the setup method. It writes the combined class and removes
the original test files. Promoted setup assignments use code snippets, while the
remaining edits manipulate Spoon's source model.

OpenRewrite uses its own `org.openrewrite.java.JavaParser`, supplying the
subject's production sources through `dependsOn`. It compares printed setup
statements, builds a class skeleton from a formatted source string, appends the
trimmed test methods, and reparses the result. A recipe replaces one source file
and deletes the other. This implementation does not use `JavaTemplate` or the
separate JavaParser library.

Run this spike's acceptance tests from the Róża Edge root with JDK 25:

```sh
./gradlew :spikes:test --tests UniversityRefactoringAcceptanceTest
```

The harness copies the subject into separate temporary directories for the
baseline and each candidate, excluding `.git`, `.gradle`, and `build`. It
applies each transformation to its copy, checks the resulting source structure,
and runs the subject's `spotlessApply check`. It requires exactly two passing
subject tests with the expected method and class names, no failures, errors, or
skipped tests. Source checks require the combined class, removal of the original
files, one setup method, the expected fields, and retention of the original
actions and assertions. The subject submodule itself is not modified.

## Findings

The check passed after organizing this spike: compilation, Java formatting, and
all three acceptance cases succeeded. The baseline, Spoon, and OpenRewrite cases
each ran two passing subject tests. Both candidates produced the expected
combined class and shared setup while preserving these two tested scenarios.

The implementations remain specific to the subject: source paths and method
names are fixed, exactly six shared setup statements are expected, and variable
selection relies on simple names. **Spoon compares setup structurally**;
OpenRewrite compares its printed form. The current checks do not establish
correctness for shadowed variables, different imports, inherited tests, or other
subjects, and they do not measure refactoring efficiency.

This establishes feasibility for the controlled example, but the engine's
technology choice remains open. OpenRewrite's source-string approach does not
yet show whether `JavaTemplate` would simplify this transformation. Evaluating
that API can continue within this spike; discovering and refactoring duplication
without knowledge of the application requires a separate investigation.
