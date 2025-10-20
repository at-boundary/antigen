package io.antigen.model;

import lombok.Value;

@Value
public class CompilationError {
    String filePath;
    int lineNumber;
    String errorMessage;

    @Override
    public String toString() {
        return String.format("%s:%d - %s", filePath, lineNumber, errorMessage);
    }
}
