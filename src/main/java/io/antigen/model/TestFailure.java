package io.antigen.model;

import lombok.Value;

@Value
public class TestFailure {
    String className;
    String methodName;
    String errorMessage;
    String stackTrace;

    @Override
    public String toString() {
        return String.format("%s.%s(): %s", className, methodName, errorMessage);
    }
}
