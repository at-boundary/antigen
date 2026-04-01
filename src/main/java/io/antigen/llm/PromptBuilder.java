package io.antigen.llm;

import io.antigen.orchestrator.GenerationContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class PromptBuilder {

    private static final String DEFAULT_TEMPLATE = """
        Generate comprehensive JUnit 5 tests for the API specification.

        API SPECIFICATION FILE: {SPEC_PATH}
        IMPORTANT: Read this file using the Read tool. Do NOT assume its contents. Do NOT create scripts to read files.

        REQUIREMENTS:
        - Write all tests in src/test/java/generated/ directory
        - Use RestAssured for HTTP calls
        - Use AssertJ for assertions
        - Test all endpoints and HTTP methods
        - Include happy path (2xx)
        - Do not test bad request tests that generate 4xx, 5xx status codes
        - Validate response schemas and required fields
        - Check against null values, empty arrays, boundary conditions, missing fields
        - Use descriptive test method names
        - Add @Test annotation to each test method
        - Organize tests by endpoint/resource in separate test classes

        IMPORTANT:
        - Generate ONLY valid Java code
        - Do NOT include markdown formatting or code blocks
        - Each test class should be in its own file
        - Include proper imports (JUnit, RestAssured, AssertJ)
        - Set base URI using RestAssured.baseURI
        {ADDITIONAL_REQUIREMENTS}
        {FEEDBACK}
        """;

    public String buildPrompt(GenerationContext context) throws IOException {
        String template = loadTemplate(context);

        Path relativePath = context.getProjectPath().relativize(context.getSpecPath());
        String specPathRelative = relativePath.toString().replace('\\', '/');

        String prompt = template.replace("{SPEC_PATH}", specPathRelative);

        if (!context.getRequirements().isEmpty()) {
            StringBuilder reqBuilder = new StringBuilder("\nADDITIONAL REQUIREMENTS:\n");
            for (String req : context.getRequirements()) {
                reqBuilder.append("- ").append(req).append("\n");
            }
            prompt = prompt.replace("{ADDITIONAL_REQUIREMENTS}", reqBuilder.toString());
        } else {
            prompt = prompt.replace("{ADDITIONAL_REQUIREMENTS}", "");
        }

        if (context.hasFeedback()) {
            String feedback = "\nPREVIOUS ATTEMPT FAILED:\n" +
                    context.getLatestFeedback().getFeedback() +
                    "\n\nPlease fix these issues and regenerate the tests.\n";
            prompt = prompt.replace("{FEEDBACK}", feedback);
        } else {
            prompt = prompt.replace("{FEEDBACK}", "");
        }

        log.debug("Built prompt with {} characters", prompt.length());
        return prompt;
    }

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

    private String loadTemplate(GenerationContext context) throws IOException {
        if (context.getPromptTemplatePath() != null) {
            log.info("Loading custom prompt template from: {}", context.getPromptTemplatePath());
            return Files.readString(context.getPromptTemplatePath());
        }
        log.debug("Using default prompt template");
        return DEFAULT_TEMPLATE;
    }
}
