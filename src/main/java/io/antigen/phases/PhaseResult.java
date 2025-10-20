package io.antigen.phases;

public interface PhaseResult {
    boolean isSuccess();
    String getFeedback();

    default boolean failed() {
        return !isSuccess();
    }
}
