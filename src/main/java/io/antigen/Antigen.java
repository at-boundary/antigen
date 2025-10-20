package io.antigen;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.antigen.model.GenerationResult;
import io.antigen.orchestrator.AntigenConfig;
import io.antigen.orchestrator.Orchestrator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "antigen",
        version = "Antigen 1.0.0",
        description = "Self-validating, reinforced LLM test generation with MetaTest",
        mixinStandardHelpOptions = true
)
public class Antigen implements Callable<Integer> {

    @Command(name = "generate", description = "Generate tests from API spec")
    public static class GenerateCommand implements Callable<Integer> {

        @Option(names = {"-s", "--spec"}, required = true, description = "Path to API specification (OpenAPI/Swagger YAML)")
        private String specPath;

        @Option(names = {"-p", "--project"}, required = true, description = "Path to target Java project")
        private String projectPath;

        @Option(names = {"-r", "--requirements"}, description = "Additional test requirements (can be specified multiple times)", split = ",")
        private List<String> requirements = new ArrayList<>();

        @Option(names = {"--requirements-file"}, description = "JSON file with requirements")
        private String requirementsFile;

        @Option(names = {"--max-retries"}, description = "Maximum retry attempts (default: 5)", defaultValue = "5")
        private int maxRetries;

        @Option(names = {"--verbose"}, description = "Enable verbose logging")
        private boolean verbose;

        @Option(names = {"--timeout-build"}, description = "Build timeout in minutes (default: 5)", defaultValue = "5")
        private int buildTimeoutMinutes;

        @Option(names = {"--timeout-test"}, description = "Test timeout in minutes (default: 10)", defaultValue = "10")
        private int testTimeoutMinutes;

        @Option(names = {"--timeout-metatest"}, description = "MetaTest timeout in minutes (default: 30)", defaultValue = "30")
        private int metatestTimeoutMinutes;

        @Override
        public Integer call() {
            try {
                Path spec = Paths.get(specPath);
                if (!Files.exists(spec)) {
                    System.err.println("Error: API spec file not found: " + specPath);
                    return 1;
                }

                Path project = Paths.get(projectPath);
                if (!Files.exists(project)) {
                    System.err.println("Error: Project directory not found: " + projectPath);
                    return 1;
                }

                if (requirementsFile != null) {
                    requirements.addAll(loadRequirementsFromFile(requirementsFile));
                }

                AntigenConfig config = AntigenConfig.builder()
                        .maxRetries(maxRetries)
                        .verbose(verbose)
                        .buildTimeout(Duration.ofMinutes(buildTimeoutMinutes))
                        .testTimeout(Duration.ofMinutes(testTimeoutMinutes))
                        .metaTestTimeout(Duration.ofMinutes(metatestTimeoutMinutes))
                        .build();

                Orchestrator orchestrator = new Orchestrator(config);
                GenerationResult result = orchestrator.generate(spec, project, requirements);

                System.out.println();
                System.out.println("=".repeat(60));
                if (result.isSuccess()) {
                    System.out.println("SUCCESS!");
                    System.out.println("Generated and validated " + result.getGeneratedFiles().size() + " test files");
                    System.out.println("Attempts: " + result.getAttempts());
                    System.out.println();
                    System.out.println("Generated files:");
                    result.getGeneratedFiles().forEach(file ->
                            System.out.println("  - " + project.relativize(file))
                    );
                    return 0;
                } else {
                    System.err.println("FAILED");
                    System.err.println("Attempts: " + result.getAttempts());
                    System.err.println("Error: " + result.getErrorMessage());
                    return 1;
                }

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                if (verbose) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        private List<String> loadRequirementsFromFile(String filePath) throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            RequirementsFile reqFile = mapper.readValue(Paths.get(filePath).toFile(), RequirementsFile.class);
            return reqFile.requirements;
        }

        private static class RequirementsFile {
            public List<String> requirements;
        }
    }

    @Command(name = "debug", description = "Debug Claude CLI integration")
    public static class DebugCommand implements Callable<Integer> {

        @Option(names = {"-p", "--project"}, required = true, description = "Path to target Java project")
        private String projectPath;

        @Override
        public Integer call() {
            try {
                Path project = Paths.get(projectPath);
                if (!Files.exists(project)) {
                    System.err.println("Error: Project directory not found: " + projectPath);
                    return 1;
                }

                System.out.println("=== Claude CLI Debug Test ===");
                System.out.println("Project: " + project);
                System.out.println();

                AntigenConfig config = AntigenConfig.builder()
                        .verbose(true)
                        .llmTimeout(Duration.ofMinutes(2))
                        .build();

                System.out.println("Detected Claude command: " + config.getClaudeCommand());
                System.out.println();

                Path generatedDir = project.resolve("src/test/java/generated");
                if (!Files.exists(generatedDir)) {
                    System.out.println("Creating test output directory: " + generatedDir);
                    Files.createDirectories(generatedDir);
                }

                String prompt = "Create a simple Java class at src/test/java/generated/DebugTest.java with a single JUnit test method that always passes.";

                System.out.println("Executing Claude CLI with simple prompt...");
                System.out.println("Prompt: " + prompt);
                System.out.println();

                io.antigen.runners.ProcessExecutor executor = new io.antigen.runners.ProcessExecutor();

                String claudeCmd = config.getClaudeCommand();
                String[] commandArgs;
                Path workingDir;

                String projectPathNormalized = project.toAbsolutePath().toString().replace('\\', '/');
                Path testFilePath = project.resolve("src/test/java/generated/DebugTest.java");
                String testFilePathNormalized = testFilePath.toString().replace('\\', '/');

                String fullPrompt = String.format(
                    "Create a simple Java class at %s with a single JUnit test method that always passes. Use the Write tool with the absolute path.",
                    testFilePathNormalized
                );

                if (claudeCmd.startsWith("node;")) {
                    String cliPath = claudeCmd.substring(5);
                    Path cliDir = Path.of(cliPath).getParent();

                    commandArgs = new String[]{
                        "node", cliPath,
                        "--print", fullPrompt,
                        "--output-format", "text",
                        "--allowedTools", "Write,Read",
                        "--permission-mode", "acceptEdits",
                        "--add-dir", projectPathNormalized
                    };
                    workingDir = cliDir;
                } else {
                    commandArgs = new String[]{
                        claudeCmd,
                        "--print", fullPrompt,
                        "--output-format", "text",
                        "--allowedTools", "Write,Read",
                        "--permission-mode", "acceptEdits"
                    };
                    workingDir = project;
                }

                io.antigen.runners.ProcessExecutor.ProcessCommand command =
                    io.antigen.runners.ProcessExecutor.ProcessCommand.builder()
                        .command(commandArgs)
                        .workingDirectory(workingDir)
                        .timeout(config.getLlmTimeout())
                        .verbose(true)
                        .build();

                io.antigen.runners.ProcessExecutor.ProcessResult result = executor.execute(command);

                System.out.println();
                System.out.println("=== Result ===");
                System.out.println("Success: " + result.isSuccess());
                System.out.println("Timed out: " + result.isTimedOut());
                System.out.println("Exit code: " + result.getExitCode());
                System.out.println();
                System.out.println("Output:");
                System.out.println(result.getOutput());

                return result.isSuccess() ? 0 : 1;

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }

    @Command(name = "version", description = "Show version information")
    public static class VersionCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("Antigen 1.0.0");
            System.out.println("Self-validating LLM test generation with MetaTest");
            return 0;
        }
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new Antigen())
                .addSubcommand("generate", new GenerateCommand())
                .addSubcommand("debug", new DebugCommand())
                .addSubcommand("version", new VersionCommand());

        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}
