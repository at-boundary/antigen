package io.antigen.model;

import lombok.Value;

@Value
public class EscapedFault {
    String endpoint;
    String field;
    String faultType;
    String testName;

    @Override
    public String toString() {
        return String.format("[%s] %s.%s - Test '%s' did not catch this fault",
            endpoint, field, faultType, testName);
    }

    public String toDetailedString() {
        return String.format("""
            Endpoint: %s
            Field: %s
            Fault Type: %s
            Test: %s
            Expected: Test should fail when '%s' is %s
            Actual: Test passed (did not detect the fault)
            """, endpoint, field, faultType, testName, field, faultType);
    }
}
