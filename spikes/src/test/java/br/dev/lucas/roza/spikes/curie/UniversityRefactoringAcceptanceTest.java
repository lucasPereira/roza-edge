package br.dev.lucas.roza.spikes.curie;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.dev.lucas.roza.spikes.curie.openrewrite.OpenRewriteUniversityTestRefactoring;
import br.dev.lucas.roza.spikes.curie.spoon.SpoonUniversityTestRefactoring;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

class UniversityRefactoringAcceptanceTest {

    private static final String TEST_PACKAGE = "br.dev.lucas.university";
    private static final Set<String> EXCLUDED_ENTRIES = Set.of(".git", ".gradle", "build");

    private static final Set<String> TEST_METHODS =
            Set.of("shouldCancelStudentEnrollment()", "shouldNotEnrollStudentTwice()");

    private final Path subjectDirectory = Path.of(System.getProperty("universitySystemSubjectDirectory"));

    @TempDir
    Path temporaryDirectory;

    @Test
    void baselineHasTwoPassingTests() throws Exception {
        Path project = copySubjectTo(temporaryDirectory.resolve("baseline"));

        String cancelClass = "%s.CancelStudentEnrollmentTest".formatted(TEST_PACKAGE);
        String enrollClass = "%s.EnrollStudentTwiceTest".formatted(TEST_PACKAGE);
        assertTwoPassingTests(project, Set.of(cancelClass, enrollClass));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refactorings")
    void groupsTheTestsWithoutChangingTheirBehavior(String name, Refactoring refactoring) throws Exception {
        Path project = copySubjectTo(temporaryDirectory.resolve(name.toLowerCase()));

        refactoring.apply(project);

        assertTransformedSource(project);
        assertTwoPassingTests(project, Set.of("%s.UniversityTest".formatted(TEST_PACKAGE)));
    }

    private static Stream<Arguments> refactorings() {
        return Stream.of(
                Arguments.of("Spoon", (Refactoring) new SpoonUniversityTestRefactoring()::refactor),
                Arguments.of("OpenRewrite", (Refactoring) new OpenRewriteUniversityTestRefactoring()::refactor));
    }

    private void assertTransformedSource(Path project) throws Exception {
        Path sourceDirectory = project.resolve("src/test/java/br/dev/lucas/university");
        Path universityTest = sourceDirectory.resolve("UniversityTest.java");

        assertAll(
                () -> assertTrue(Files.exists(universityTest)),
                () -> assertFalse(Files.exists(sourceDirectory.resolve("CancelStudentEnrollmentTest.java"))),
                () -> assertFalse(Files.exists(sourceDirectory.resolve("EnrollStudentTwiceTest.java"))));

        String source = Files.readString(universityTest);

        assertAll(
                () -> assertEquals(1, occurrences(source, "class UniversityTest {")),
                () -> assertEquals(0, occurrences(source, "class UniversityTest extends")),
                () -> assertEquals(1, occurrences(source, "private Student alice;")),
                () -> assertEquals(1, occurrences(source, "private Course algorithms;")),
                () -> assertEquals(1, occurrences(source, "@BeforeEach"), source),
                () -> assertEquals(2, occurrences(source, "@Test")),
                () -> assertEquals(1, occurrences(source, "new University(\"UFSC\")"), source),
                () -> assertEquals(0, occurrences(source, "Student alice =")),
                () -> assertEquals(0, occurrences(source, "Course algorithms =")),
                () -> assertEquals(1, occurrences(source, "void shouldCancelStudentEnrollment()")),
                () -> assertEquals(1, occurrences(source, "void shouldNotEnrollStudentTwice()")),
                () -> assertEquals(1, occurrences(source, "boolean success = algorithms.cancelEnrollment(alice);")),
                () -> assertEquals(1, occurrences(source, "assertTrue(success);")),
                () -> assertEquals(1, occurrences(source, "assertTrue(algorithms.students().isEmpty());")),
                () -> assertEquals(1, occurrences(source, "boolean success = algorithms.enroll(alice);")),
                () -> assertEquals(1, occurrences(source, "assertFalse(success);")),
                () -> assertEquals(1, occurrences(source, "assertEquals(List.of(alice), algorithms.students());")));
    }

    private void assertTwoPassingTests(Path project, Set<String> expectedClasses) throws Exception {
        BuildResult build = formatAndCheck(project);
        assertEquals(0, build.exitCode(), build.output());

        TestResults results = readTestResults(project);
        assertAll(
                () -> assertEquals(2, results.tests()),
                () -> assertEquals(0, results.failures()),
                () -> assertEquals(0, results.errors()),
                () -> assertEquals(0, results.skipped()),
                () -> assertEquals(expectedClasses, results.classNames()),
                () -> assertEquals(TEST_METHODS, results.methodNames()));
    }

    private static int occurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }

    private Path copySubjectTo(Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(subjectDirectory)) {
            for (Path source : paths.toList()) {
                Path relativePath = subjectDirectory.relativize(source);
                if (isExcluded(relativePath)) {
                    continue;
                }

                Path target = destination.resolve(relativePath);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, COPY_ATTRIBUTES);
                }
            }
        }

        return destination;
    }

    private boolean isExcluded(Path relativePath) {
        return EXCLUDED_ENTRIES.contains(relativePath.getName(0).toString());
    }

    private BuildResult formatAndCheck(Path projectDirectory) throws IOException, InterruptedException {
        String wrapper = projectDirectory.resolve("gradlew").toString();
        List<String> command = List.of(wrapper, "spotlessApply", "check", "--no-daemon", "--console=plain");
        Process process = new ProcessBuilder(command)
                .directory(projectDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        return new BuildResult(process.waitFor(), output);
    }

    private TestResults readTestResults(Path projectDirectory) throws Exception {
        Path reportsDirectory = projectDirectory.resolve("build/test-results/test");
        Set<String> classNames = new HashSet<>();
        Set<String> methodNames = new HashSet<>();
        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;

        for (Path report : testReportsIn(reportsDirectory)) {
            TestResults reportResults = readTestReport(report);
            tests += reportResults.tests();
            failures += reportResults.failures();
            errors += reportResults.errors();
            skipped += reportResults.skipped();
            classNames.addAll(reportResults.classNames());
            methodNames.addAll(reportResults.methodNames());
        }

        return new TestResults(tests, failures, errors, skipped, classNames, methodNames);
    }

    private List<Path> testReportsIn(Path reportsDirectory) throws IOException {
        try (Stream<Path> reports = Files.list(reportsDirectory)) {
            return reports.filter(this::isTestReport).toList();
        }
    }

    private boolean isTestReport(Path report) {
        return report.getFileName().toString().startsWith("TEST-");
    }

    private TestResults readTestReport(Path report) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document document = builder.parse(report.toFile());
        NodeList testCases = document.getElementsByTagName("testcase");
        Set<String> classNames = new HashSet<>();
        Set<String> methodNames = new HashSet<>();

        for (int index = 0; index < testCases.getLength(); index++) {
            NamedNodeMap testCase = testCases.item(index).getAttributes();
            classNames.add(testCase.getNamedItem("classname").getNodeValue());
            methodNames.add(testCase.getNamedItem("name").getNodeValue());
        }

        int failures = document.getElementsByTagName("failure").getLength();
        int errors = document.getElementsByTagName("error").getLength();
        int skipped = document.getElementsByTagName("skipped").getLength();

        return new TestResults(testCases.getLength(), failures, errors, skipped, classNames, methodNames);
    }

    @FunctionalInterface
    private interface Refactoring {
        void apply(Path projectDirectory) throws Exception;
    }

    private record BuildResult(int exitCode, String output) {}

    private record TestResults(
            int tests, int failures, int errors, int skipped, Set<String> classNames, Set<String> methodNames) {}
}
