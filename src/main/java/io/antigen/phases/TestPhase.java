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

        StringBuilder feedback = new StringBuilder("TEST FAILURES:\n\n");
        for (TestFailure failure : testFailures) {
            feedback.append("Test: ").append(failure.getClassName())
                    .append(".").append(failure.getMethodName()).append("()\n");
            feedback.append("Error: ").append(failure.getErrorMessage()).append("\n");
            feedback.append("Stack trace:\n").append(failure.getStackTrace()).append("\n");
            feedback.append("---\n\n");
        }
        return feedback.toString();
    }
}
