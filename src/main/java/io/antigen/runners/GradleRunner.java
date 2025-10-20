package io.antigen.runners;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.antigen.feedback.ErrorParser;
import io.antigen.model.CompilationError;
import io.antigen.model.EscapedFault;
import io.antigen.model.TestFailure;
import io.antigen.orchestrator.AntigenConfig;
import io.antigen.orchestrator.GenerationContext;
import io.antigen.phases.BuildPhase;
import io.antigen.phases.MetaTestPhase;
import io.antigen.phases.TestPhase;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class GradleRunner {

    private final ProcessExecutor processExecutor;
    private final ErrorParser errorParser;
    private final AntigenConfig config;

    public GradleRunner(AntigenConfig config) {
        this.processExecutor = new ProcessExecutor();
        this.errorParser = new ErrorParser();
        this.config = config;
    }

    public BuildPhase build(GenerationContext context) {
        log.info("Building project...");

        String gradleCommand = getGradleCommand(context.getProjectPath());

        ProcessExecutor.ProcessCommand command = ProcessExecutor.ProcessCommand.builder()
                .command(gradleCommand, "clean", "compileTestJava", "--console=plain", "--no-daemon")
                .workingDirectory(context.getProjectPath())
                .timeout(config.getBuildTimeout())
                .verbose(config.isVerbose())
                .build();

        ProcessExecutor.ProcessResult result = processExecutor.execute(command);

        if (result.isSuccess()) {
            log.info("Build successful");
            return BuildPhase.success();
        }

        log.warn("Build failed");
        List<CompilationError> errors = errorParser.parseCompilationErrors(result.getOutput());

        if (errors.isEmpty()) {
            errors = List.of(new CompilationError("unknown", 0,
                    errorParser.extractErrorSummary(result.getOutput())));
        }

        return BuildPhase.failed(errors);
    }

    public TestPhase runTests(GenerationContext context) {
        log.info("Running tests (without MetaTest)...");

        String gradleCommand = getGradleCommand(context.getProjectPath());

        ProcessExecutor.ProcessCommand command = ProcessExecutor.ProcessCommand.builder()
                .command(
                        gradleCommand,
                        "test",
                        "--tests", "generated.*",
                        "-DrunWithMetatest=false",
                        "--console=plain",
                        "--no-daemon"
                )
                .workingDirectory(context.getProjectPath())
                .timeout(config.getTestTimeout())
                .verbose(config.isVerbose())
                .build();

        ProcessExecutor.ProcessResult result = processExecutor.execute(command);

        if (result.isSuccess()) {
            log.info("All tests passed");
            return TestPhase.success();
        }

        log.warn("Tests failed");
        List<TestFailure> failures = errorParser.parseTestFailures(result.getOutput());

        if (failures.isEmpty()) {
            failures = List.of(new TestFailure("unknown", "unknown",
                    errorParser.extractErrorSummary(result.getOutput()), result.getOutput()));
        }

        return TestPhase.failed(failures);
    }

    public MetaTestPhase runMetaTest(GenerationContext context) {
        log.info("Running tests with MetaTest fault injection...");

        String gradleCommand = getGradleCommand(context.getProjectPath());

        ProcessExecutor.ProcessCommand command = ProcessExecutor.ProcessCommand.builder()
                .command(
                        gradleCommand,
                        "test",
                        "--tests", "generated.*",
                        "-DrunWithMetatest=true",
                        "-Dmetatest.config.source=local",
                        "--console=plain",
                        "--no-daemon"
                )
                .workingDirectory(context.getProjectPath())
                .timeout(config.getMetaTestTimeout())
                .verbose(config.isVerbose())
                .build();

        ProcessExecutor.ProcessResult result = processExecutor.execute(command);

        try {
            MetaTestReport report = parseMetaTestReport(context.getProjectPath());

            if (report.getEscapedFaults().isEmpty()) {
                log.info("MetaTest passed - all faults caught");
                return MetaTestPhase.success(report.getFaultDetectionRate());
            }

            log.warn("MetaTest failed - {} faults escaped", report.getEscapedFaults().size());
            return MetaTestPhase.failed(report.getEscapedFaults(), report.getFaultDetectionRate());

        } catch (IOException e) {
            log.error("Failed to parse MetaTest report", e);
            return MetaTestPhase.failed(
                    List.of(new EscapedFault("unknown", "unknown", "unknown", "unknown")),
                    0.0
            );
        }
    }

    private MetaTestReport parseMetaTestReport(Path projectPath) throws IOException {
        Path reportPath = projectPath.resolve("build/reports/metatest/report.json");

        if (!Files.exists(reportPath)) {
            throw new IOException("MetaTest report not found at: " + reportPath);
        }

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(reportPath.toFile(), MetaTestReport.class);
    }

    private String getGradleCommand(Path projectPath) {
        Path gradlewUnix = projectPath.resolve("gradlew");
        Path gradlewWindows = projectPath.resolve("gradlew.bat");

        if (Files.exists(gradlewWindows)) {
            return gradlewWindows.toString();
        } else if (Files.exists(gradlewUnix)) {
            return gradlewUnix.toString();
        }

        return "gradle";
    }

    /**
     * DTO for parsing MetaTest JSON report
     * This structure should match what MetaTest outputs
     */
    @Data
    public static class MetaTestReport {
        private int totalFaults;
        private int caughtFaults;
        private List<EscapedFault> escapedFaults;
        private double faultDetectionRate;

        public MetaTestReport() {}

        public List<EscapedFault> getEscapedFaults() {
            return escapedFaults != null ? escapedFaults : List.of();
        }

    }
}
