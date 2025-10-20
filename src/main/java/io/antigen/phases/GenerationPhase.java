package io.antigen.phases;

import lombok.Value;

import java.nio.file.Path;
import java.util.List;

@Value
public class GenerationPhase implements PhaseResult {
    boolean success;
    List<Path> generatedFiles;
    String errorMessage;

    public static GenerationPhase success(List<Path> files) {
        return new GenerationPhase(true, files, null);
    }

    public static GenerationPhase failed(String error) {
        return new GenerationPhase(false, List.of(), error);
    }

    @Override
    public String getFeedback() {
        return errorMessage != null ? errorMessage : "Generation completed successfully";
    }
}
