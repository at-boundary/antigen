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
                return MetaTestPhase.success(
                    report.getFaultDetectionRate(),
                    report.getTotalFaults(),
                    report.getCaughtFaults()
                );
            }

            log.warn("MetaTest failed - {} faults escaped", report.getEscapedFaults().size());
            return MetaTestPhase.failed(
                report.getEscapedFaults(),
                report.getFaultDetectionRate(),
                report.getTotalFaults(),
                report.getCaughtFaults()
            );

        } catch (IOException e) {
            log.error("Failed to parse MetaTest report", e);
            return MetaTestPhase.failed(
                    List.of(new EscapedFault("unknown", "unknown", "unknown", "unknown")),
                    0.0,
                    1,
                    0
            );
        }
    }

    private MetaTestReport parseMetaTestReport(Path projectPath) throws IOException {
        Path reportPath = projectPath.resolve("fault_simulation_report.json");

        if (!Files.exists(reportPath)) {
            throw new IOException("MetaTest report not found at: " + reportPath);
        }

        ObjectMapper mapper = new ObjectMapper();
        // New structure: { "/endpoint": { counters..., "contract_faults": {faultType: {field: result}}, "invariant_faults": {name: result} } }
        java.util.Map<String, EndpointReport> rawReport =
                mapper.readValue(reportPath.toFile(), new com.fasterxml.jackson.core.type.TypeReference<>() {});

        java.util.List<EscapedFault> escapedFaults = new java.util.ArrayList<>();
        int totalFaults = 0;
        int caughtFaults = 0;

        for (java.util.Map.Entry<String, EndpointReport> endpointEntry : rawReport.entrySet()) {
            String endpoint = endpointEntry.getKey();
            EndpointReport endpointReport = endpointEntry.getValue();

            totalFaults += endpointReport.getContractFaultCount() + endpointReport.getInvariantFaultCount();
            caughtFaults += endpointReport.getContractFaultsCaught() + endpointReport.getInvariantFaultsCaught();

            // Escaped contract faults: contract_faults -> faultType -> fieldName
            for (java.util.Map.Entry<String, java.util.Map<String, FaultFieldResult>> faultTypeEntry
                    : endpointReport.getContractFaults().entrySet()) {
                String faultType = faultTypeEntry.getKey();
                for (java.util.Map.Entry<String, FaultFieldResult> fieldEntry
                        : faultTypeEntry.getValue().entrySet()) {
                    String fieldName = fieldEntry.getKey();
                    FaultFieldResult result = fieldEntry.getValue();
                    if (!result.isCaughtByAnyTest()) {
                        escapedFaults.add(new EscapedFault(
                                endpoint, fieldName, faultType,
                                String.join(", ", result.getTestedBy())));
                    }
                }
            }

            // Escaped invariant faults: invariant_faults -> invariantName
            for (java.util.Map.Entry<String, FaultFieldResult> invariantEntry
                    : endpointReport.getInvariantFaults().entrySet()) {
                String invariantName = invariantEntry.getKey();
                FaultFieldResult result = invariantEntry.getValue();
                if (!result.isCaughtByAnyTest()) {
                    escapedFaults.add(new EscapedFault(
                            endpoint, invariantName, "invariant",
                            String.join(", ", result.getTestedBy())));
                }
            }
        }

        double faultDetectionRate = totalFaults > 0 ? (double) caughtFaults / totalFaults : 0.0;

        MetaTestReport report = new MetaTestReport();
        report.setTotalFaults(totalFaults);
        report.setCaughtFaults(caughtFaults);
        report.setEscapedFaults(escapedFaults);
        report.setFaultDetectionRate(faultDetectionRate);

        return report;
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
     * DTO for one endpoint entry in the MetaTest report.
     * Structure: { contractFaultCount, invariantFaultCount, contractFaultsCaught, invariantFaultsCaught,
     *              contract_faults: { faultType: { fieldName: FaultFieldResult } },
     *              invariant_faults: { invariantName: FaultFieldResult } }
     */
    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class EndpointReport {
        @com.fasterxml.jackson.annotation.JsonProperty("contractFaultCount")
        private int contractFaultCount;
        @com.fasterxml.jackson.annotation.JsonProperty("invariantFaultCount")
        private int invariantFaultCount;
        @com.fasterxml.jackson.annotation.JsonProperty("contractFaultsCaught")
        private int contractFaultsCaught;
        @com.fasterxml.jackson.annotation.JsonProperty("invariantFaultsCaught")
        private int invariantFaultsCaught;
        @com.fasterxml.jackson.annotation.JsonProperty("contract_faults")
        private java.util.Map<String, java.util.Map<String, FaultFieldResult>> contractFaults;
        @com.fasterxml.jackson.annotation.JsonProperty("invariant_faults")
        private java.util.Map<String, FaultFieldResult> invariantFaults;

        public EndpointReport() {}

        public java.util.Map<String, java.util.Map<String, FaultFieldResult>> getContractFaults() {
            return contractFaults != null ? contractFaults : java.util.Map.of();
        }

        public java.util.Map<String, FaultFieldResult> getInvariantFaults() {
            return invariantFaults != null ? invariantFaults : java.util.Map.of();
        }
    }

    /**
     * DTO for a single fault result (contract field or invariant).
     * Structure: { "caught_by_any_test": true/false, "tested_by": [...], "caught_by": [...] }
     */
    @Data
    public static class FaultFieldResult {
        @com.fasterxml.jackson.annotation.JsonProperty("caught_by_any_test")
        private boolean caughtByAnyTest;
        @com.fasterxml.jackson.annotation.JsonProperty("tested_by")
        private java.util.List<String> testedBy;
        @com.fasterxml.jackson.annotation.JsonProperty("caught_by")
        private java.util.List<FaultTestResult> caughtBy;

        public FaultFieldResult() {}

        public java.util.List<String> getTestedBy() {
            return testedBy != null ? testedBy : java.util.List.of();
        }
    }

    /**
     * DTO for an individual test result within a fault entry.
     */
    @Data
    public static class FaultTestResult {
        private String test;
        private boolean caught;
        private String error;

        public FaultTestResult() {}
    }

    /**
     * DTO for MetaTest report summary
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
