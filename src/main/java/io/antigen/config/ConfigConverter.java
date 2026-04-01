package io.antigen.config;

import io.antigen.orchestrator.AntigenConfig;

import java.time.Duration;

public class ConfigConverter {

    public static AntigenConfig toAntigenConfig(YamlConfig yamlConfig) {
        AntigenConfig.AntigenConfigBuilder builder = AntigenConfig.builder()
                .maxRetries(yamlConfig.getMaxRetries())
                .faultDetectionThreshold(yamlConfig.getValidation().getFaultDetectionThreshold());

        if (yamlConfig.getTimeouts() != null) {
            builder.llmTimeout(parseDuration(yamlConfig.getTimeouts().getLlm()))
                   .buildTimeout(parseDuration(yamlConfig.getTimeouts().getBuild()))
                   .testTimeout(parseDuration(yamlConfig.getTimeouts().getTest()))
                   .metaTestTimeout(parseDuration(yamlConfig.getTimeouts().getMetatest()));
        }

        return builder.build();
    }

    private static Duration parseDuration(String duration) {
        if (duration == null || duration.isBlank()) {
            return Duration.ofMinutes(5);
        }

        duration = duration.trim().toLowerCase();

        try {
            if (duration.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(duration.substring(0, duration.length() - 2)));
            } else if (duration.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(duration.substring(0, duration.length() - 1)));
            } else if (duration.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(duration.substring(0, duration.length() - 1)));
            } else if (duration.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(duration.substring(0, duration.length() - 1)));
            } else {
                return Duration.ofSeconds(Long.parseLong(duration));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration format: " + duration +
                ". Use format like '5m', '300s', '1h'", e);
        }
    }
}
