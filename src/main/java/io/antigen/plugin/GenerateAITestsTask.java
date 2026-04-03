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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class GenerateAITestsTask extends DefaultTask {

    @TaskAction
    public void generateTests() {
        try {
            File projectDir = getProject().getProjectDir();

            YamlConfigLoader loader = new YamlConfigLoader();
            YamlConfig yamlConfig = loader.load(projectDir);

            Path projectPath = projectDir.toPath();
            Path specPath = resolvePathFromProject(projectPath, yamlConfig.getSpec());
            Path promptTemplatePath = resolvePromptTemplate(projectPath, yamlConfig.getPromptTemplate());

            getLogger().lifecycle("=== Antigen Test Generation ===");
            getLogger().lifecycle("Spec:        {}", specPath);
            getLogger().lifecycle("Output:      src/test/java/{}", yamlConfig.getOutputDir());
            getLogger().lifecycle("Max retries: {}", yamlConfig.getMaxRetries());
            getLogger().lifecycle("MetaTest:    {}", yamlConfig.getValidation().isEnabled() ? "enabled" : "disabled");
            getLogger().lifecycle("");

            AntigenConfig antigenConfig = ConfigConverter.toAntigenConfig(yamlConfig);

            Orchestrator orchestrator = new Orchestrator(antigenConfig);
            GenerationResult result = orchestrator.generate(
                specPath,
                projectPath,
                yamlConfig.getRequirements(),
                promptTemplatePath
            );

            if (result.isSuccess()) {
                getLogger().lifecycle("");
                getLogger().lifecycle("=== SUCCESS ===");
                getLogger().lifecycle("Generated {} test files in {} attempt(s)", result.getGeneratedFiles().size(), result.getAttempts());
                result.getGeneratedFiles().forEach(file ->
                    getLogger().lifecycle("  - {}", projectPath.relativize(file)));
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
