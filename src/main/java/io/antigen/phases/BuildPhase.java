package io.antigen.phases;

import io.antigen.model.CompilationError;
import lombok.Value;

import java.util.List;

@Value
public class BuildPhase implements PhaseResult {
    boolean success;
    List<CompilationError> compilationErrors;

    public static BuildPhase success() {
        return new BuildPhase(true, List.of());
    }

    public static BuildPhase failed(List<CompilationError> errors) {
        return new BuildPhase(false, errors);
    }

    @Override
    public String getFeedback() {
        if (success) {
            return "Build completed successfully";
        }

        StringBuilder feedback = new StringBuilder("COMPILATION ERRORS:\n\n");
        for (CompilationError error : compilationErrors) {
            feedback.append(error.toString()).append("\n");
        }
        return feedback.toString();
    }
}
