package br.dev.lucas.roza.spikes.curie.spoon;

import static spoon.reflect.cu.SourcePosition.NOPOSITION;
import static spoon.reflect.declaration.ModifierKind.PRIVATE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.refactoring.Refactoring;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.factory.Factory;
import spoon.reflect.factory.MethodFactory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.equals.EqualsVisitor;

public final class SpoonUniversityTestRefactoring {

    private static final String PACKAGE_NAME = "br.dev.lucas.university";
    private static final String CANCEL_CLASS = "%s.CancelStudentEnrollmentTest".formatted(PACKAGE_NAME);
    private static final String ENROLL_CLASS = "%s.EnrollStudentTwiceTest".formatted(PACKAGE_NAME);
    private static final int EXPECTED_SETUP_STATEMENTS = 6;

    public void refactor(Path projectDirectory) throws IOException {
        Path testSourceDirectory = projectDirectory.resolve("src/test/java");
        Path packageDirectory = testSourceDirectory.resolve("br/dev/lucas/university");
        Path cancelSource = packageDirectory.resolve("CancelStudentEnrollmentTest.java");
        Path enrollSource = packageDirectory.resolve("EnrollStudentTwiceTest.java");

        Launcher launcher = createLauncher(cancelSource, enrollSource);
        CtModel model = launcher.buildModel();
        CtClass<?> cancelClass = requireClass(model, CANCEL_CLASS);
        CtClass<?> enrollClass = requireClass(model, ENROLL_CLASS);
        CtMethod<?> cancelMethod = requireMethod(cancelClass, "shouldCancelStudentEnrollment");
        CtMethod<?> enrollMethod = requireMethod(enrollClass, "shouldNotEnrollStudentTwice");

        int setupStatements = sharedPrefixLength(cancelMethod, enrollMethod);
        if (setupStatements != EXPECTED_SETUP_STATEMENTS) {
            String message = "Expected six shared setup statements but found %d".formatted(setupStatements);
            throw new IllegalStateException(message);
        }

        transform(launcher.getFactory(), enrollClass, enrollMethod, cancelMethod, setupStatements);

        launcher.setOutputFilter(type -> type == enrollClass);
        launcher.setSourceOutputDirectory(testSourceDirectory.toFile());
        launcher.prettyprint();

        Path transformedSource = packageDirectory.resolve("UniversityTest.java");
        if (!Files.exists(transformedSource)) {
            throw new IllegalStateException("Spoon did not generate UniversityTest.java");
        }

        Files.delete(cancelSource);
        Files.delete(enrollSource);
    }

    private Launcher createLauncher(Path cancelSource, Path enrollSource) {
        Launcher launcher = new Launcher();
        Environment environment = launcher.getEnvironment();
        environment.setComplianceLevel(25);
        environment.setNoClasspath(true);
        environment.setAutoImports(true);
        launcher.addInputResource(cancelSource.toString());
        launcher.addInputResource(enrollSource.toString());
        return launcher;
    }

    private void transform(
            Factory factory,
            CtClass<?> targetClass,
            CtMethod<?> targetMethod,
            CtMethod<?> sourceMethod,
            int setupStatements) {
        List<CtStatement> sharedSetup = clonePrefix(targetMethod, setupStatements);
        Set<String> promotedNames = findPromotedVariableNames(targetMethod, sourceMethod, setupStatements);
        Map<String, CtField<?>> fields = createFields(factory, targetClass, sharedSetup, promotedNames);

        CtMethod<?> copiedMethod = factory.Method().create(targetClass, sourceMethod, true);
        trimSetup(targetMethod, setupStatements);
        trimSetup(copiedMethod, setupStatements);
        rebindVariableAccesses(targetMethod, fields);
        rebindVariableAccesses(copiedMethod, fields);

        CtMethod<?> setupMethod = createSetupMethod(factory, targetClass, sharedSetup, fields);
        Refactoring.changeTypeName(targetClass, "UniversityTest");

        List<CtTypeMember> members = new ArrayList<>();
        members.addAll(fields.values());
        members.add(setupMethod);
        members.add(copiedMethod);
        members.add(targetMethod);
        targetClass.setTypeMembers(members);
    }

    private List<CtStatement> clonePrefix(CtMethod<?> method, int length) {
        List<CtStatement> sharedSetup = method.getBody().getStatements().subList(0, length);
        List<CtStatement> statements = new ArrayList<>();
        for (CtStatement statement : sharedSetup) {
            CtStatement copy = statement.clone();
            copy.setPositions(NOPOSITION);
            statements.add(copy);
        }
        return statements;
    }

    private Set<String> findPromotedVariableNames(
            CtMethod<?> firstMethod, CtMethod<?> secondMethod, int setupStatements) {
        List<CtStatement> sharedSetup = firstMethod.getBody().getStatements().subList(0, setupStatements);
        Set<String> setupVariableNames = new LinkedHashSet<>();
        for (CtStatement statement : sharedSetup) {
            if (statement instanceof CtLocalVariable<?> variable) {
                setupVariableNames.add(variable.getSimpleName());
            }
        }

        Set<String> promotedNames = new LinkedHashSet<>();
        collectVariableAccesses(firstMethod, setupStatements, setupVariableNames, promotedNames);
        collectVariableAccesses(secondMethod, setupStatements, setupVariableNames, promotedNames);
        return promotedNames;
    }

    private void collectVariableAccesses(
            CtMethod<?> method, int setupStatements, Set<String> setupVariableNames, Set<String> promotedNames) {
        List<CtStatement> statements = method.getBody().getStatements();
        List<CtStatement> testStatements = statements.subList(setupStatements, statements.size());
        testStatements.stream()
                .flatMap(statement -> statement.getElements(new TypeFilter<>(CtVariableAccess.class)).stream())
                .map(access -> access.getVariable().getSimpleName())
                .filter(setupVariableNames::contains)
                .forEach(promotedNames::add);
    }

    private Map<String, CtField<?>> createFields(
            Factory factory, CtClass<?> targetClass, List<CtStatement> sharedSetup, Set<String> promotedNames) {
        Map<String, CtField<?>> fields = new LinkedHashMap<>();

        for (CtStatement statement : sharedSetup) {
            if (statement instanceof CtLocalVariable<?> variable && promotedNames.contains(variable.getSimpleName())) {
                CtField<?> field = createField(factory, targetClass, variable);
                field.setPositions(NOPOSITION);
                fields.put(variable.getSimpleName(), field);
            }
        }

        if (!fields.keySet().equals(promotedNames)) {
            throw new IllegalStateException("Every value used after setup must come from a setup local variable");
        }

        return fields;
    }

    private <T> CtField<T> createField(Factory factory, CtClass<?> targetClass, CtLocalVariable<T> variable) {
        CtTypeReference<T> type = variable.getType().clone();
        String name = variable.getSimpleName();
        return factory.Field().create(targetClass, Set.of(PRIVATE), type, name);
    }

    private CtMethod<?> createSetupMethod(
            Factory factory, CtClass<?> targetClass, List<CtStatement> sharedSetup, Map<String, CtField<?>> fields) {
        CtBlock<Void> body = factory.Core().createBlock();

        for (CtStatement statement : sharedSetup) {
            if (statement instanceof CtLocalVariable<?> variable && fields.containsKey(variable.getSimpleName())) {
                String assignment = "%s = %s".formatted(variable.getSimpleName(), variable.getAssignment());
                CtCodeSnippetStatement setupStatement = factory.Code().createCodeSnippetStatement(assignment);
                setupStatement.setPositions(NOPOSITION);
                body.addStatement(setupStatement);
            } else {
                rebindVariableAccesses(statement, fields);
                body.addStatement(statement);
            }
        }

        MethodFactory methodFactory = factory.Method();
        CtTypeReference<Void> returnType = factory.Type().voidPrimitiveType();

        CtMethod<Void> setupMethod =
                methodFactory.create(targetClass, Set.of(), returnType, "setUp", List.of(), Set.of(), body);

        setupMethod.setPositions(NOPOSITION);
        annotateBeforeEach(factory, setupMethod);
        return setupMethod;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void annotateBeforeEach(Factory factory, CtMethod<?> setupMethod) {
        CtTypeReference beforeEach = factory.Type().createReference("org.junit.jupiter.api.BeforeEach");
        factory.Annotation().annotate(setupMethod, beforeEach);
    }

    private void trimSetup(CtMethod<?> method, int setupStatements) {
        List<CtStatement> statements = method.getBody().getStatements();
        List<CtStatement> testStatements = statements.subList(setupStatements, statements.size());
        method.getBody().setStatements(new ArrayList<>(testStatements));
    }

    private void rebindVariableAccesses(CtElement element, Map<String, CtField<?>> fields) {
        for (CtVariableAccess<?> access : element.getElements(new TypeFilter<>(CtVariableAccess.class))) {
            CtField<?> field = fields.get(access.getVariable().getSimpleName());
            if (field != null) {
                setVariable(access, field);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setVariable(CtVariableAccess access, CtField<?> field) {
        access.setVariable(field.getReference());
    }

    private int sharedPrefixLength(CtMethod<?> firstMethod, CtMethod<?> secondMethod) {
        List<CtStatement> firstStatements = firstMethod.getBody().getStatements();
        List<CtStatement> secondStatements = secondMethod.getBody().getStatements();
        int limit = Math.min(firstStatements.size(), secondStatements.size());
        int length = 0;

        while (length < limit && EqualsVisitor.equals(firstStatements.get(length), secondStatements.get(length))) {
            length++;
        }

        return length;
    }

    private CtClass<?> requireClass(CtModel model, String qualifiedName) {
        return model.getAllTypes().stream()
                .filter(CtClass.class::isInstance)
                .map(CtClass.class::cast)
                .filter(type -> type.getQualifiedName().equals(qualifiedName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing class %s".formatted(qualifiedName)));
    }

    private CtMethod<?> requireMethod(CtClass<?> type, String methodName) {
        List<CtMethod<?>> methods = type.getMethodsByName(methodName);
        if (methods.size() != 1) {
            String message = "Expected one %s method in %s".formatted(methodName, type.getQualifiedName());
            throw new IllegalStateException(message);
        }
        return methods.getFirst();
    }
}
