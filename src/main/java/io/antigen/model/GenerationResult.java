package io.antigen.model;

import lombok.Value;

import java.nio.file.Path;
import java.util.List;

@Value
public class GenerationResult {
    boolean success;
    int attempts;
    List<Path> generatedFiles;
    String errorMessage;

    public static GenerationResult success(int attempts, List<Path> files) {
        return new GenerationResult(true, attempts, files, null);
    }

    public static GenerationResult failure(int attempts, String error) {
        return new GenerationResult(false, attempts, List.of(), error);
    }
}
