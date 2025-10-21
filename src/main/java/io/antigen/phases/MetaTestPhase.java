package io.antigen.phases;

import io.antigen.model.EscapedFault;
import lombok.Value;

import java.util.List;

@Value
public class MetaTestPhase implements PhaseResult {
    boolean success;
    List<EscapedFault> escapedFaults;
    double faultDetectionRate;
    int totalFaults;
    int caughtFaults;

    public static MetaTestPhase success(double detectionRate, int total, int caught) {
        return new MetaTestPhase(true, List.of(), detectionRate, total, caught);
    }

    public static MetaTestPhase failed(List<EscapedFault> escaped, double detectionRate, int total, int caught) {
        return new MetaTestPhase(false, escaped, detectionRate, total, caught);
    }

    public boolean hasEscapedFaults() {
        return !escapedFaults.isEmpty();
    }

    @Override
    public String getFeedback() {
        if (success) {
            return String.format("MetaTest passed - %.1f%% fault detection rate (%d/%d faults caught)",
                faultDetectionRate * 100, caughtFaults, totalFaults);
        }

        return String.format("""
            METATEST FAILURE - Your tests did NOT catch some simulated faults.

            Read the detailed fault report at: fault_simulation_report.json in project root

            This file contains a structured report showing:
            - Which endpoints were tested
            - Which fields had faults injected (missing_field, null_field)
            - Which tests ran against each fault
            - Whether each test caught the fault (caught: true/false)
            
            All simulated faults should have caught: true values

            Analyze this report and update your test assertions to catch the faults where "caught": false.
            Focus on adding proper null checks, field presence validation, and value type assertions.
            """,
            escapedFaults.size(),
            totalFaults,
            faultDetectionRate * 100);
    }
}
