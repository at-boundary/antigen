package io.antigen.phases;

import io.antigen.model.EscapedFault;
import lombok.Value;

import java.util.List;

@Value
public class MetaTestPhase implements PhaseResult {
    boolean success;
    List<EscapedFault> escapedFaults;
    double faultDetectionRate;

    public static MetaTestPhase success(double detectionRate) {
        return new MetaTestPhase(true, List.of(), detectionRate);
    }

    public static MetaTestPhase failed(List<EscapedFault> escaped, double detectionRate) {
        return new MetaTestPhase(false, escaped, detectionRate);
    }

    public boolean hasEscapedFaults() {
        return !escapedFaults.isEmpty();
    }

    @Override
    public String getFeedback() {
        if (success) {
            return String.format("MetaTest passed - %.1f%% fault detection rate", faultDetectionRate * 100);
        }

        StringBuilder feedback = new StringBuilder();
        feedback.append("METATEST FAILURES - Tests did NOT catch these faults:\n\n");

        for (EscapedFault fault : escapedFaults) {
            feedback.append(fault.toDetailedString()).append("\n---\n\n");
        }

        feedback.append(String.format("\nOverall fault detection rate: %.1f%%\n", faultDetectionRate * 100));
        feedback.append("Please add assertions to detect these fault scenarios.\n");

        return feedback.toString();
    }
}
