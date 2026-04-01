package io.antigen.plugin;

import io.antigen.config.ConfigConverter;
import io.antigen.config.YamlConfig;
import io.antigen.config.YamlConfigLoader;
import io.antigen.model.GenerationResult;
import io.antigen.orchestrator.AntigenConfig;
import io.antigen.orchestrator.Orchestrator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class GenerateAITestsTask extends DefaultTask {
    private static final Logger logger = LoggerFactory.getLogger(GenerateAITestsTask.class);

    @TaskAction
    public void generateTests() {
        try {
            File projectDir = getProject().getProjectDir();
            logger.info("Running Antigen test generation for project: {}", projectDir);

            YamlConfigLoader loader = new YamlConfigLoader();
            YamlConfig yamlConfig = loader.load(projectDir);

            Path projectPath = projectDir.toPath();
            Path specPath = resolvePathFromProject(projectPath, yamlConfig.getSpec());
            Path promptTemplatePath = resolvePromptTemplate(projectPath, yamlConfig.getPromptTemplate());

            logger.info("API Specification: {}", specPath);
            logger.info("Output directory: {}", yamlConfig.getOutputDir());
            logger.info("Max retries: {}", yamlConfig.getMaxRetries());
            logger.info("MetaTest validation: {}", yamlConfig.getValidation().isEnabled() ? "enabled" : "disabled");

            AntigenConfig antigenConfig = ConfigConverter.toAntigenConfig(yamlConfig);

            Orchestrator orchestrator = new Orchestrator(antigenConfig);
            GenerationResult result = orchestrator.generate(
                specPath,
                projectPath,
                yamlConfig.getRequirements(),
                promptTemplatePath
            );

            if (result.isSuccess()) {
                logger.info("SUCCESS: Tests generated in {} attempts", result.getAttempts());
                logger.info("Generated {} test files", result.getGeneratedFiles().size());
                result.getGeneratedFiles().forEach(file -> logger.info("  - {}", file));
            } else {
                throw new GradleException("Test generation failed after " + result.getAttempts() +
                    " attempts: " + result.getErrorMessage());
            }

        } catch (IOException e) {
            throw new GradleException("Failed to load configuration: " + e.getMessage(), e);
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException("Test generation failed: " + e.getMessage(), e);
        }
    }

    private Path resolvePathFromProject(Path projectPath, String relativePath) {
        Path resolved = projectPath.resolve(relativePath);
        if (!resolved.toFile().exists()) {
            throw new GradleException("Specification file not found: " + resolved);
        }
        return resolved;
    }

    private Path resolvePromptTemplate(Path projectPath, String promptTemplate) {
        if (promptTemplate == null || promptTemplate.isBlank()) {
            return null;
        }
        Path resolved = projectPath.resolve(promptTemplate);
        if (!resolved.toFile().exists()) {
            throw new GradleException("Prompt template file not found: " + resolved);
        }
        return resolved;
    }
}
