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

        // Get relative path to spec from project directory
        Path relativePath = context.getProjectPath().relativize(context.getSpecPath());
        String specPathRelative = relativePath.toString().replace('\\', '/');

        prompt.append(String.format("""
            Generate comprehensive JUnit 5 tests for the API specification.

            API SPECIFICATION FILE: %s
            Add tests ONLY FOR http://localhost:8000/api/v1/auth/login and http://localhost:8000/api/v1/orders endpoints, not all endpoints!
            
            IMPORTANT: Read this file using the Read tool. Do NOT assume its contents. Do NOT create scripts to read files

            REQUIREMENTS:
            - Write all tests in src/test/java/generated/ directory
            - Test the API requests with curl before running the tests to prevent failures - skip if can't make that request return 200
            - Register a user only once, and reuse it in all tests to authenticate.
            - Each test should call only one endpoint related to that test
            - Do not create, under no circumstances, any other files except test files in src/test/java/generated/
            - Use RestAssured for HTTP calls
            - Use AssertJ for assertions
            - Include happy path (2xx), Do not write bad request tests that generate 4xx, 5xx status codes
            - Use descriptive test method names that explain what is being tested
            - Add @Test annotation to each test method
            - Organize tests by endpoint/resource in separate test classes

            IMPORTANT:
            - Generate ONLY valid Java code
            - Do NOT include markdown formatting or code blocks
            - Each test class should be in its own file
            - Include proper imports (JUnit, RestAssured, AssertJ)
            - Set base URI using RestAssured.baseURI (check the API spec for the server URL)

            """, specPathRelative));

        if (!context.getRequirements().isEmpty()) {
            prompt.append("ADDITIONAL REQUIREMENTS:\n");
            for (String req : context.getRequirements()) {
                prompt.append("- ").append(req).append("\n");
            }
            prompt.append("\n");
        }

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
