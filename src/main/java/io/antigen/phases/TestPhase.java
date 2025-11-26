package io.antigen.phases;

import io.antigen.model.TestFailure;
import lombok.Value;

import java.util.List;

@Value
public class TestPhase implements PhaseResult {
    boolean success;
    List<TestFailure> testFailures;

    public static TestPhase success() {
        return new TestPhase(true, List.of());
    }

    public static TestPhase failed(List<TestFailure> failures) {
        return new TestPhase(false, failures);
    }

    @Override
    public String getFeedback() {
        if (success) {
            return "All tests passed";
        }

        StringBuilder feedback = new StringBuilder();
        feedback.append(String.format("TEST FAILURES - %d test(s) failed.\n\n", testFailures.size()));
        feedback.append("IMPORTANT: Read the detailed test reports for full error messages.\n");
        feedback.append("Test reports location: build/test-results/test/\n");
        feedback.append("Use the Read tool to read the XML files (TEST-*.xml) for failed tests.\n\n");
        feedback.append("Test the API requests with curl before running the tests to prevent failures - skip if can't make that request return 200");

        feedback.append("\nDo NOT write scripts to parse these files - use the Read tool directly.\n");
        feedback.append("The XML files contain <failure> elements with detailed assertion messages and stack traces.\n");

        return feedback.toString();
    }
}
