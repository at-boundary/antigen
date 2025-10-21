package io.antigen.llm;

import io.antigen.orchestrator.AntigenConfig;
import io.antigen.orchestrator.GenerationContext;
import io.antigen.phases.GenerationPhase;
import io.antigen.runners.ProcessExecutor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class ClaudeGenerator {

    private final ProcessExecutor processExecutor;
    private final PromptBuilder promptBuilder;
    private final AntigenConfig config;

    public ClaudeGenerator(AntigenConfig config) {
        this.processExecutor = new ProcessExecutor();
        this.promptBuilder = new PromptBuilder();
        this.config = config;
    }

    public GenerationPhase generate(GenerationContext context) {
        log.info("Starting test generation with Claude Code CLI");

        try {
            Path generatedDir = context.getProjectPath().resolve("src/test/java/generated");
            if (!Files.exists(generatedDir)) {
                log.info("Creating test output directory: {}", generatedDir);
                Files.createDirectories(generatedDir);
            }

            String prompt = context.hasFeedback()
                    ? promptBuilder.buildRetryPrompt(context)
                    : promptBuilder.buildPrompt(context);

            log.debug("Prompt length: {} characters", prompt.length());

            java.util.List<String> cmdList = new java.util.ArrayList<>();

            String claudeCmd = config.getClaudeCommand();
            if (claudeCmd.startsWith("node;")) {
                String cliPath = claudeCmd.substring(5);
                cmdList.add("node");
                cmdList.add(cliPath);
            } else {
                cmdList.add(claudeCmd);
            }

            cmdList.add("--print");
            cmdList.add(prompt);
            cmdList.add("--output-format");
            cmdList.add("text");
            cmdList.add("--allowedTools");
            cmdList.add(config.getAllowedTools());

            // Add permission handling
            if (config.isUseDangerousSkip()) {
                cmdList.add("--dangerously-skip-permissions");
            } else {
                cmdList.add("--permission-mode");
                cmdList.add(config.getPermissionMode());
            }

            String[] commandArgs = cmdList.toArray(new String[0]);

            ProcessExecutor.ProcessCommand command = ProcessExecutor.ProcessCommand.builder()
                    .command(commandArgs)
                    .workingDirectory(context.getProjectPath())
                    .timeout(config.getLlmTimeout())
                    .verbose(config.isVerbose())
                    .build();

            ProcessExecutor.ProcessResult result = processExecutor.execute(command);

            if (result.isTimedOut()) {
                log.error("Claude CLI timed out");
                return GenerationPhase.failed("Claude CLI timed out after " +
                        config.getLlmTimeout().toSeconds() + " seconds");
            }

            if (result.failed()) {
                log.error("Claude CLI failed with exit code: {}", result.getExitCode());
                return GenerationPhase.failed("Claude CLI failed: " + result.getOutput());
            }

            List<Path> generatedFiles = findGeneratedTestFiles(context.getProjectPath());

            if (generatedFiles.isEmpty()) {
                log.warn("No test files were generated");
                return GenerationPhase.failed("No test files found in src/test/java/generated/. " +
                        "Claude may not have created any files.");
            }

            log.info("Successfully generated {} test files", generatedFiles.size());
            for (Path file : generatedFiles) {
                log.info("  - {}", context.getProjectPath().relativize(file));
            }

            return GenerationPhase.success(generatedFiles);

        } catch (IOException e) {
            log.error("Failed to generate tests", e);
            return GenerationPhase.failed("Error during generation: " + e.getMessage());
        }
    }

    private List<Path> findGeneratedTestFiles(Path projectPath) throws IOException {
        Path generatedDir = projectPath.resolve("src/test/java/generated");

        if (!Files.exists(generatedDir)) {
            log.warn("Generated test directory does not exist: {}", generatedDir);
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(generatedDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        }
    }

    /**
     * Verify Claude CLI is available
     */
    public boolean isClaudeAvailable() {
        try {
            String claudeCmd = config.getClaudeCommand();
            String[] commandArgs;

            if (claudeCmd.startsWith("node;")) {
                String cliPath = claudeCmd.substring(5);
                commandArgs = new String[]{"node", cliPath, "--version"};
            } else {
                commandArgs = new String[]{claudeCmd, "--version"};
            }

            ProcessExecutor.ProcessCommand command = ProcessExecutor.ProcessCommand.builder()
                    .command(commandArgs)
                    .workingDirectory(Path.of("."))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            ProcessExecutor.ProcessResult result = processExecutor.execute(command);
            return result.isSuccess();

        } catch (Exception e) {
            log.error("Failed to check Claude CLI availability", e);
            return false;
        }
    }
}
