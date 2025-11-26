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
            METATEST FAILURE - Your tests did NOT catch %d out of %d injected faults (%.1f%% detection rate).

            IMPORTANT: Use the Read tool to read fault_simulation_report.json in the project root.
            DO NOT write scripts to parse it - read it directly with the Read tool.

            The report structure:
            {
              "/api/endpoint": {
                "fieldName": {
                  "fault_type": {
                    "caught_by_any_test": true/false,  <- KEY FIELD: false means this fault escaped
                    "details": [
                      { "test": "testName", "caught": true/false, "error": "..." }
                    ]
                  }
                }
              }
            }

            Your task:
            1. Read E:\\Projects\\METATEST\\ANTIGEN\\antigen-example\\fault_simulation_report.json
            2. Find all entries where "caught_by_any_test": false
            3. Look at the "details" array to see which tests failed to catch it
            4. Update those tests to add proper assertions to catch that particular fault

            Focus on the faults where "caught_by_any_test": false - these are the ones that escaped detection.
            "caught_by_any_test" is set to true when at least one test catches that fault 
            """,
            escapedFaults.size(),
            totalFaults,
            faultDetectionRate * 100);
    }
}
