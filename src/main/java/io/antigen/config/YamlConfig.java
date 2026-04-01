package io.antigen.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class YamlConfig {

    private String spec;
    private String outputDir = "generated";
    private int maxRetries = 5;
    private List<String> requirements = new ArrayList<>();
    private String promptTemplate;
    private Timeouts timeouts = new Timeouts();
    private Validation validation = new Validation();

    @Data
    public static class Timeouts {
        private String llm = "5m";
        private String build = "5m";
        private String test = "10m";
        private String metatest = "30m";
    }

    @Data
    public static class Validation {
        private boolean enabled = true;
        private double faultDetectionThreshold = 1.0;
    }

    public void validate() {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("spec is required in antigen.yml");
        }
        if (outputDir == null || outputDir.isBlank()) {
            throw new IllegalArgumentException("outputDir is required in antigen.yml");
        }
        if (maxRetries < 1) {
            throw new IllegalArgumentException("maxRetries must be at least 1");
        }
        if (validation.faultDetectionThreshold < 0.0 || validation.faultDetectionThreshold > 1.0) {
            throw new IllegalArgumentException("faultDetectionThreshold must be between 0.0 and 1.0");
        }
    }
}
