package io.antigen.runners;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ProcessExecutor {

    public ProcessResult execute(ProcessCommand command) {
        log.info("Executing: {} in {}", String.join(" ", command.getCommand()), command.getWorkingDirectory());

        ProcessBuilder pb = new ProcessBuilder(command.getCommand());
        pb.directory(command.getWorkingDirectory().toFile());
        pb.redirectErrorStream(true);

        if (command.getEnvironment() != null) {
            pb.environment().putAll(command.getEnvironment());
        }

        try {
            Process process = pb.start();

            process.getOutputStream().close();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (command.isVerbose()) {
                        log.info(line);
                    }
                }
            }

            boolean completed = process.waitFor(
                    command.getTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );

            if (!completed) {
                process.destroyForcibly();
                return ProcessResult.timeout(command.getTimeout(), output.toString());
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString();

            if (exitCode == 0) {
                log.info("Process completed successfully");
                return ProcessResult.success(outputStr);
            } else {
                log.warn("Process failed with exit code: {}", exitCode);
                return ProcessResult.failure(exitCode, outputStr);
            }

        } catch (IOException e) {
            log.error("Failed to execute process", e);
            return ProcessResult.error(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Process interrupted", e);
            return ProcessResult.error("Process interrupted: " + e.getMessage());
        }
    }

    public static class ProcessCommand {
        private final String[] command;
        private final Path workingDirectory;
        private final Duration timeout;
        private final boolean verbose;
        private final java.util.Map<String, String> environment;

        private ProcessCommand(Builder builder) {
            this.command = builder.command;
            this.workingDirectory = builder.workingDirectory;
            this.timeout = builder.timeout;
            this.verbose = builder.verbose;
            this.environment = builder.environment;
        }

        public String[] getCommand() {
            return command;
        }

        public Path getWorkingDirectory() {
            return workingDirectory;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public boolean isVerbose() {
            return verbose;
        }

        public java.util.Map<String, String> getEnvironment() {
            return environment;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String[] command;
            private Path workingDirectory;
            private Duration timeout = Duration.ofMinutes(5);
            private boolean verbose = false;
            private java.util.Map<String, String> environment;

            public Builder command(String... command) {
                this.command = command;
                return this;
            }

            public Builder workingDirectory(Path workingDirectory) {
                this.workingDirectory = workingDirectory;
                return this;
            }

            public Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }

            public Builder verbose(boolean verbose) {
                this.verbose = verbose;
                return this;
            }

            public Builder environment(java.util.Map<String, String> environment) {
                this.environment = environment;
                return this;
            }

            public ProcessCommand build() {
                if (command == null || command.length == 0) {
                    throw new IllegalArgumentException("Command cannot be null or empty");
                }
                if (workingDirectory == null) {
                    throw new IllegalArgumentException("Working directory cannot be null");
                }
                return new ProcessCommand(this);
            }
        }
    }

    @Data
    public static class ProcessResult {
        private final boolean success;
        private final int exitCode;
        private final String output;
        private final String errorMessage;
        private final boolean timedOut;

        private ProcessResult(boolean success, int exitCode, String output, String errorMessage, boolean timedOut) {
            this.success = success;
            this.exitCode = exitCode;
            this.output = output;
            this.errorMessage = errorMessage;
            this.timedOut = timedOut;
        }

        public static ProcessResult success(String output) {
            return new ProcessResult(true, 0, output, null, false);
        }

        public static ProcessResult failure(int exitCode, String output) {
            return new ProcessResult(false, exitCode, output, null, false);
        }

        public static ProcessResult timeout(Duration timeout, String output) {
            return new ProcessResult(false, -1, output,
                    "Process timed out after " + timeout.toSeconds() + " seconds", true);
        }

        public static ProcessResult error(String errorMessage) {
            return new ProcessResult(false, -1, "", errorMessage, false);
        }

        public boolean failed() {
            return !success;
        }
    }
}
