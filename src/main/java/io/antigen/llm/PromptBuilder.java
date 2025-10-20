package io.antigen.llm;

import io.antigen.orchestrator.GenerationContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class PromptBuilder {

    public String buildPrompt(GenerationContext context) throws IOException {
        StringBuilder prompt = new StringBuilder();

        // Read API spec content
        String specContent = Files.readString(context.getSpecPath());

        // Build normalized paths for Claude (forward slashes work cross-platform)
        String projectPathNormalized = normalizePath(context.getProjectPath());
        String testOutputPath = normalizePath(context.getProjectPath().resolve("src/test/java/generated"));

        // Base instruction
        prompt.append(String.format("""
            Generate comprehensive JUnit 5 tests for the API specification below.

            PROJECT PATH: %s

            REQUIREMENTS:
            - Write all tests in %s directory
            - Use ABSOLUTE paths when writing files
            - Use RestAssured for HTTP calls
            - Use AssertJ for assertions
            - Test all endpoints and HTTP methods
            - Include happy path (2xx) and error cases (4xx, 5xx)
            - Validate response schemas and required fields
            - Test edge cases: null values, empty arrays, boundary conditions
            - Use descriptive test method names that explain what is being tested
            - Add @Test annotation to each test method
            - Organize tests by endpoint/resource in separate test classes

            IMPORTANT:
            - Generate ONLY valid Java code
            - Do NOT include markdown formatting or code blocks
            - Each test class should be in its own file
            - Include proper imports (JUnit, RestAssured, AssertJ)
            - Set base URI if needed (you can use RestAssured.baseURI)

            """, projectPathNormalized, testOutputPath));

        // Add custom requirements if present
        if (!context.getRequirements().isEmpty()) {
            prompt.append("ADDITIONAL REQUIREMENTS:\n");
            for (String req : context.getRequirements()) {
                prompt.append("- ").append(req).append("\n");
            }
            prompt.append("\n");
        }

        // Add API specification
        prompt.append("API SPECIFICATION:\n");
        prompt.append("```yaml\n");
        prompt.append(specContent);
        prompt.append("\n```\n\n");

        // Add feedback from previous failures if present
        if (context.hasFeedback()) {
            prompt.append("PREVIOUS ATTEMPT FAILED:\n");
            prompt.append(context.getLatestFeedback().getFeedback());
            prompt.append("\n\nPlease fix these issues and regenerate the tests.\n");
        }

        log.debug("Built prompt with {} characters", prompt.length());
        return prompt.toString();
    }

    /**
     * Create a simplified prompt for retry attempts
     */
    public String buildRetryPrompt(GenerationContext context) {
        if (!context.hasFeedback()) {
            throw new IllegalStateException("Cannot build retry prompt without feedback");
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("The previous test generation failed with the following issues:\n\n");
        prompt.append(context.getLatestFeedback().getFeedback());
        prompt.append("\n\nPlease fix ONLY these specific issues. ");
        prompt.append("Review the existing test files in src/test/java/generated/ and make the necessary corrections.\n");

        return prompt.toString();
    }

    /**
     * Normalize path to use forward slashes (works cross-platform with Claude CLI)
     * Example: E:\Projects\test -> E:/Projects/test
     */
    private String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
