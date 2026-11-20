package br.dev.lucas.roza.spikes.curie.openrewrite;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.tree.ParseError;

public final class OpenRewriteUniversityTestRefactoring {

    private static final Path TEST_SOURCE_DIRECTORY = Path.of("src/test/java/br/dev/lucas/university");
    private static final Path CANCEL_SOURCE = TEST_SOURCE_DIRECTORY.resolve("CancelStudentEnrollmentTest.java");
    private static final Path ENROLL_SOURCE = TEST_SOURCE_DIRECTORY.resolve("EnrollStudentTwiceTest.java");
    private static final Path TRANSFORMED_SOURCE = TEST_SOURCE_DIRECTORY.resolve("UniversityTest.java");
    private static final int EXPECTED_SETUP_STATEMENTS = 6;

    private static final String UNIVERSITY_TEST_TEMPLATE = """
            package br.dev.lucas.university;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertFalse;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            import java.util.List;
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.Test;

            class UniversityTest {

            %s

                @BeforeEach
                void setUp() {
            %s
                }
            }
            """;

    public void refactor(Path projectDirectory) throws IOException {
        ExecutionContext context = new InMemoryExecutionContext(error -> {
            throw new IllegalStateException("OpenRewrite failed", error);
        });
        JavaParser parser = createParser(projectDirectory);
        Parser.Input cancelInput = Parser.Input.fromFile(projectDirectory.resolve(CANCEL_SOURCE));
        Parser.Input enrollInput = Parser.Input.fromFile(projectDirectory.resolve(ENROLL_SOURCE));
        List<SourceFile> sourceFiles = parse(parser, List.of(cancelInput, enrollInput), projectDirectory, context);

        J.CompilationUnit cancel = requireCompilationUnit(sourceFiles, CANCEL_SOURCE);
        J.CompilationUnit enroll = requireCompilationUnit(sourceFiles, ENROLL_SOURCE);
        J.MethodDeclaration cancelMethod = requireMethod(cancel, "shouldCancelStudentEnrollment");
        J.MethodDeclaration enrollMethod = requireMethod(enroll, "shouldNotEnrollStudentTwice");

        int setupStatements = sharedPrefixLength(cancelMethod, enrollMethod);
        if (setupStatements != EXPECTED_SETUP_STATEMENTS) {
            String message = "Expected six shared setup statements but found %d".formatted(setupStatements);
            throw new IllegalStateException(message);
        }

        String universityTestSource = createUniversityTestSource(cancelMethod, enrollMethod, setupStatements);
        J.MethodDeclaration trimmedCancel = trimSetup(cancelMethod, setupStatements);
        J.MethodDeclaration trimmedEnroll = trimSetup(enrollMethod, setupStatements);
        J.CompilationUnit skeleton = parseUniversityTest(parser, projectDirectory, context, universityTestSource);
        J.CompilationUnit merged = appendTestMethods(skeleton, trimmedCancel, trimmedEnroll);
        J.CompilationUnit replacement = parseUniversityTest(parser, projectDirectory, context, merged.printAll());

        Recipe recipe = replacementRecipe(replacement);
        InMemoryLargeSourceSet sources = new InMemoryLargeSourceSet(sourceFiles);
        RecipeRun run = recipe.run(sources, context);
        List<Result> results = run.getChangeset().getAllResults();
        if (results.size() != 2) {
            throw new IllegalStateException("Expected one replacement and one deletion");
        }

        applyResults(projectDirectory, results);
    }

    private JavaParser createParser(Path projectDirectory) throws IOException {
        Path mainSourceDirectory = projectDirectory.resolve("src/main/java");
        List<Parser.Input> mainSources;

        try (Stream<Path> paths = Files.walk(mainSourceDirectory)) {
            mainSources = paths.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(Parser.Input::fromFile)
                    .toList();
        }

        return JavaParser.fromJavaVersion().dependsOn(mainSources).build();
    }

    private J.CompilationUnit appendTestMethods(
            J.CompilationUnit skeleton, J.MethodDeclaration cancelMethod, J.MethodDeclaration enrollMethod) {
        J.ClassDeclaration skeletonClass = skeleton.getClasses().getFirst();
        List<Statement> members = new ArrayList<>(skeletonClass.getBody().getStatements());
        members.add(cancelMethod);
        members.add(enrollMethod);

        J.Block mergedBody = skeletonClass.getBody().withStatements(members);
        J.ClassDeclaration mergedClass = skeletonClass.withBody(mergedBody);
        return skeleton.withClasses(List.of(mergedClass));
    }

    private J.CompilationUnit parseUniversityTest(
            JavaParser parser, Path projectDirectory, ExecutionContext context, String source) {
        Path sourcePath = projectDirectory.resolve(TRANSFORMED_SOURCE);
        Parser.Input input = Parser.Input.fromString(sourcePath, source);
        List<SourceFile> sourceFiles = parse(parser.reset(), List.of(input), projectDirectory, context);

        return requireCompilationUnit(sourceFiles, TRANSFORMED_SOURCE);
    }

    private List<SourceFile> parse(
            JavaParser parser, List<Parser.Input> inputs, Path projectDirectory, ExecutionContext context) {
        Stream<SourceFile> parsedSources = parser.parseInputs(inputs, projectDirectory, context);
        List<SourceFile> sourceFiles = parsedSources.toList();
        for (SourceFile sourceFile : sourceFiles) {
            if (sourceFile instanceof ParseError error) {
                throw error.toException();
            }
        }
        return sourceFiles;
    }

    private J.CompilationUnit requireCompilationUnit(List<SourceFile> sourceFiles, Path sourcePath) {
        return sourceFiles.stream()
                .filter(J.CompilationUnit.class::isInstance)
                .map(J.CompilationUnit.class::cast)
                .filter(source -> source.getSourcePath().equals(sourcePath))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing source %s".formatted(sourcePath)));
    }

    private J.MethodDeclaration requireMethod(J.CompilationUnit source, String methodName) {
        J.ClassDeclaration declaration = source.getClasses().getFirst();
        List<Statement> statements = declaration.getBody().getStatements();
        List<J.MethodDeclaration> methods = statements.stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .map(J.MethodDeclaration.class::cast)
                .filter(method -> method.getSimpleName().equals(methodName))
                .toList();

        if (methods.size() != 1) {
            String message = "Expected one %s method in %s".formatted(methodName, source.getSourcePath());
            throw new IllegalStateException(message);
        }
        return methods.getFirst();
    }

    private int sharedPrefixLength(J.MethodDeclaration firstMethod, J.MethodDeclaration secondMethod) {
        List<Statement> firstStatements = statementsOf(firstMethod);
        List<Statement> secondStatements = statementsOf(secondMethod);
        int limit = Math.min(firstStatements.size(), secondStatements.size());
        int length = 0;

        while (length < limit) {
            String firstSource = firstStatements.get(length).toString();
            String secondSource = secondStatements.get(length).toString();
            if (!firstSource.equals(secondSource)) {
                break;
            }
            length++;
        }

        return length;
    }

    private String createUniversityTestSource(
            J.MethodDeclaration firstMethod, J.MethodDeclaration secondMethod, int setupStatements) {
        List<Statement> sharedSetup = statementsOf(firstMethod).subList(0, setupStatements);
        Set<String> setupVariableNames = new LinkedHashSet<>();

        for (Statement statement : sharedSetup) {
            if (statement instanceof J.VariableDeclarations variables) {
                variables.getVariables().stream()
                        .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                        .forEach(setupVariableNames::add);
            }
        }

        Set<String> usedNames = new LinkedHashSet<>();
        collectUsedNames(firstMethod, setupStatements, usedNames);
        collectUsedNames(secondMethod, setupStatements, usedNames);
        setupVariableNames.retainAll(usedNames);

        List<String> fields = new ArrayList<>();
        List<String> setup = new ArrayList<>();
        for (Statement statement : sharedSetup) {
            if (statement instanceof J.VariableDeclarations declaration
                    && declaresSharedVariable(declaration, setupVariableNames)) {
                J.VariableDeclarations.NamedVariable variable = requireSingleVariable(declaration);
                TypeTree type = declaration.getTypeExpression();
                String name = variable.getSimpleName();
                Expression initializer = Objects.requireNonNull(variable.getInitializer());

                fields.add("    private %s %s;".formatted(type, name));
                setup.add("        %s = %s;".formatted(name, initializer));
            } else {
                setup.add(indent("%s;".formatted(statement), 8));
            }
        }

        return UNIVERSITY_TEST_TEMPLATE.formatted(String.join("\n", fields), String.join("\n", setup));
    }

    private boolean declaresSharedVariable(J.VariableDeclarations declaration, Set<String> sharedVariableNames) {
        return declaration.getVariables().stream()
                .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                .anyMatch(sharedVariableNames::contains);
    }

    private void collectUsedNames(J.MethodDeclaration method, int setupStatements, Set<String> usedNames) {
        List<Statement> statements = statementsOf(method);
        JavaVisitor<Set<String>> visitor = new JavaVisitor<>() {
            @Override
            public J visitIdentifier(J.Identifier identifier, Set<String> names) {
                names.add(identifier.getSimpleName());
                return identifier;
            }
        };
        for (Statement statement : statements.subList(setupStatements, statements.size())) {
            visitor.visit(statement, usedNames);
        }
    }

    private J.VariableDeclarations.NamedVariable requireSingleVariable(J.VariableDeclarations variables) {
        if (variables.getVariables().size() != 1) {
            throw new IllegalStateException("A promoted declaration must contain one variable");
        }
        return variables.getVariables().getFirst();
    }

    private List<Statement> statementsOf(J.MethodDeclaration method) {
        return Objects.requireNonNull(method.getBody()).getStatements();
    }

    private String indent(String source, int spaces) {
        String indentation = " ".repeat(spaces);
        return source.lines().map(indentation::concat).collect(Collectors.joining("\n"));
    }

    private J.MethodDeclaration trimSetup(J.MethodDeclaration method, int setupStatements) {
        J.Block body = Objects.requireNonNull(method.getBody());
        List<Statement> originalStatements = body.getStatements();
        List<Statement> testStatements = originalStatements.subList(setupStatements, originalStatements.size());
        List<Statement> statements = new ArrayList<>(testStatements);
        Statement firstStatement = statements.getFirst();
        statements.set(0, (Statement) firstStatement.withPrefix(Space.format("\n        ")));
        return method.withBody(body.withStatements(statements));
    }

    private Recipe replacementRecipe(J.CompilationUnit replacement) {
        return new Recipe() {
            @Override
            public String getDisplayName() {
                return "Group the university tests";
            }

            @Override
            public String getDescription() {
                return "Groups the two university tests that share the same setup.";
            }

            @Override
            public TreeVisitor<?, ExecutionContext> getVisitor() {
                return new JavaVisitor<>() {
                    @Override
                    public J visitCompilationUnit(J.CompilationUnit source, ExecutionContext context) {
                        if (source.getSourcePath().equals(CANCEL_SOURCE)) {
                            return replacement.withId(source.getId());
                        }
                        if (source.getSourcePath().equals(ENROLL_SOURCE)) {
                            return null;
                        }
                        return source;
                    }
                };
            }
        };
    }

    private void applyResults(Path projectDirectory, List<Result> results) throws IOException {
        for (Result result : results) {
            SourceFile before = result.getBefore();
            SourceFile after = result.getAfter();

            if (before != null && (after == null || !before.getSourcePath().equals(after.getSourcePath()))) {
                Files.deleteIfExists(projectDirectory.resolve(before.getSourcePath()));
            }

            if (after != null) {
                Path target = projectDirectory.resolve(after.getSourcePath());
                Files.createDirectories(target.getParent());
                Files.writeString(target, after.printAll(), UTF_8);
            }
        }
    }
}
