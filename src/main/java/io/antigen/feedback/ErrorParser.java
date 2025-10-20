package io.antigen.feedback;

import io.antigen.model.CompilationError;
import io.antigen.model.TestFailure;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ErrorParser {

    private static final Pattern COMPILATION_ERROR_PATTERN = Pattern.compile(
            "(.+\\.java):(\\d+):\\s*error:\\s*(.+)"
    );

    private static final Pattern TEST_FAILURE_PATTERN = Pattern.compile(
            "([\\w.]+)\\s*>\\s*(\\w+)\\(\\)\\s*FAILED"
    );

    public List<CompilationError> parseCompilationErrors(String gradleOutput) {
        List<CompilationError> errors = new ArrayList<>();

        String[] lines = gradleOutput.split("\\n");
        for (String line : lines) {
            Matcher matcher = COMPILATION_ERROR_PATTERN.matcher(line);
            if (matcher.find()) {
                String filePath = matcher.group(1);
                int lineNumber = Integer.parseInt(matcher.group(2));
                String errorMessage = matcher.group(3).trim();

                errors.add(new CompilationError(filePath, lineNumber, errorMessage));
            }
        }

        log.info("Parsed {} compilation errors", errors.size());
        return errors;
    }

    public List<TestFailure> parseTestFailures(String gradleOutput) {
        List<TestFailure> failures = new ArrayList<>();

        String[] lines = gradleOutput.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = TEST_FAILURE_PATTERN.matcher(lines[i]);
            if (matcher.find()) {
                String className = matcher.group(1);
                String methodName = matcher.group(2);

                StringBuilder errorMessage = new StringBuilder();
                StringBuilder stackTrace = new StringBuilder();
                boolean inStackTrace = false;

                for (int j = i + 1; j < Math.min(i + 50, lines.length); j++) {
                    String line = lines[j];

                    if (line.contains("> ") && line.contains("FAILED")) {
                        break;
                    }

                    if (line.trim().isEmpty() && inStackTrace) {
                        break;
                    }

                    if (line.contains("Exception") || line.contains("Error:") ||
                            line.contains("AssertionError") || line.contains("expected")) {
                        if (errorMessage.length() == 0) {
                            errorMessage.append(line.trim());
                        }
                        inStackTrace = true;
                    }

                    if (inStackTrace) {
                        stackTrace.append(line).append("\n");
                    }
                }

                String error = errorMessage.length() > 0 ? errorMessage.toString() : "Test failed";
                String stack = stackTrace.length() > 0 ? stackTrace.toString() : "No stack trace available";

                failures.add(new TestFailure(className, methodName, error, stack));
            }
        }

        log.info("Parsed {} test failures", failures.size());
        return failures;
    }

    /**
     * Extracts a summary of the error for quick diagnosis
     */
    public String extractErrorSummary(String output) {
        String[] lines = output.split("\\n");
        for (String line : lines) {
            if (line.contains("error:") || line.contains("FAILED") ||
                    line.contains("Exception") || line.contains("AssertionError")) {
                return line.trim();
            }
        }
        return "No specific error found";
    }
}
