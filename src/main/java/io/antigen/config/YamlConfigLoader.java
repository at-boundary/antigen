package io.antigen.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class YamlConfigLoader {
    private static final String CONFIG_FILE_NAME = "antigen.yml";

    private final ObjectMapper yamlMapper;

    public YamlConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public YamlConfig load(Path projectDir) throws IOException {
        Path configPath = projectDir.resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configPath)) {
            throw new IOException(
                String.format("Configuration file not found: %s%nPlease create antigen.yml in your project root.",
                    configPath)
            );
        }

        log.info("Loading configuration from: {}", configPath);
        YamlConfig config = yamlMapper.readValue(configPath.toFile(), YamlConfig.class);
        config.validate();
        log.info("Configuration loaded: spec={}, outputDir={}, maxRetries={}",
            config.getSpec(), config.getOutputDir(), config.getMaxRetries());

        return config;
    }

    public YamlConfig load(File projectDir) throws IOException {
        return load(projectDir.toPath());
    }
}
