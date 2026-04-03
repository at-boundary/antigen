package io.antigen.orchestrator;

import io.antigen.llm.ClaudeGenerator;
import io.antigen.model.GenerationResult;
import io.antigen.phases.BuildPhase;
import io.antigen.phases.GenerationPhase;
import io.antigen.phases.MetaTestPhase;
import io.antigen.phases.TestPhase;
import io.antigen.runners.GradleRunner;
import java.nio.file.Path;
import java.util.List;

public class Orchestrator {

    private final ClaudeGenerator claudeGenerator;
    private final GradleRunner gradleRunner;
    private final AntigenConfig config;

    public Orchestrator(AntigenConfig config) {
        this.config = config;
        this.claudeGenerator = new ClaudeGenerator(config);
        this.gradleRunner = new GradleRunner(config);
    }

    public GenerationResult generate(Path specPath, Path projectPath, List<String> requirements) {
        return generate(specPath, projectPath, requirements, null);
    }

    public GenerationResult generate(Path specPath, Path projectPath, List<String> requirements, Path promptTemplatePath) {
        if (promptTemplatePath != null) {
            System.out.println("Custom Prompt Template: " + promptTemplatePath);
        }

        if (!claudeGenerator.isClaudeAvailable()) {
            System.out.println("ERROR: Claude CLI is not available. Please install Claude Code and ensure 'claude' command is in PATH.");
            return GenerationResult.failure(0, "Claude CLI not found. Install Claude Code first.");
        }

        GenerationContext context = GenerationContext.builder()
                .specPath(specPath)
                .projectPath(projectPath)
                .promptTemplatePath(promptTemplatePath)
                .requirements(requirements)
                .build();

        for (int attempt = 1; attempt <= config.getMaxRetries(); attempt++) {
            System.out.println();
            System.out.printf("=== Attempt %d/%d ===%n", attempt, config.getMaxRetries());

            System.out.println("State 1: Generating tests with Claude...");
            GenerationPhase genPhase = claudeGenerator.generate(context);
            System.out.println("Result: " + (genPhase.isSuccess() ? "SUCCESS" : "FAILED"));

            if (genPhase.failed()) {
                System.out.println("Generation failed: " + genPhase.getFeedback());
                context = context.addFeedback(genPhase);
                continue;
            }

            System.out.println("State 2: Building project...");
            BuildPhase buildPhase = gradleRunner.build(context);
            System.out.println("Result: " + (buildPhase.isSuccess() ? "SUCCESS" : "FAILED"));

            if (buildPhase.failed()) {
                System.out.printf("Build failed with %d errors%n", buildPhase.getCompilationErrors().size());
                context = context.addFeedback(buildPhase);
                continue;
            }

            System.out.println("State 3: Running tests (without MetaTest)...");
            TestPhase testPhase = gradleRunner.runTests(context);
            System.out.println("Result: " + (testPhase.isSuccess() ? "SUCCESS" : "FAILED"));

            if (testPhase.failed()) {
                System.out.printf("Tests failed: %d failures%n", testPhase.getTestFailures().size());
                context = context.addFeedback(testPhase);
                continue;
            }

            System.out.println("State 4: Running tests with MetaTest fault injection...");
            MetaTestPhase metaTestPhase = gradleRunner.runMetaTest(context);
            System.out.println("Result: " + (metaTestPhase.isSuccess() ? "SUCCESS" : "FAILED"));
            System.out.printf("Fault Detection Rate: %.1f%%%n", metaTestPhase.getFaultDetectionRate() * 100);

            if (metaTestPhase.hasEscapedFaults()) {
                System.out.printf("MetaTest failed: %d faults escaped%n", metaTestPhase.getEscapedFaults().size());

                if (shouldRetry(context, attempt)) {
                    context = context.addFeedback(metaTestPhase);
                    continue;
                } else {
                    System.out.println("Same MetaTest failures repeating, stopping retries");
                    return GenerationResult.failure(attempt,
                            "MetaTest failures are repeating. Generated tests may be at maximum quality.");
                }
            }

            System.out.println("=== SUCCESS ===");
            System.out.printf("Tests generated and validated in %d attempts%n", attempt);
            return GenerationResult.success(attempt, genPhase.getGeneratedFiles());
        }

        System.out.println("=== FAILURE ===");
        System.out.printf("Failed to generate valid tests after %d attempts%n", config.getMaxRetries());
        return GenerationResult.failure(config.getMaxRetries(),
                "Maximum retries exceeded. Last error: " + context.getLatestFeedback().getFeedback());
    }

    private boolean shouldRetry(GenerationContext context, int currentAttempt) {
        if (currentAttempt >= config.getMaxRetries()) {
            return false;
        }

        if (context.getFeedbackHistory().size() >= 2) {
            List<MetaTestPhase> recentMetaTestPhases = context.getFeedbackHistory().stream()
                    .filter(phase -> phase instanceof MetaTestPhase)
                    .map(phase -> (MetaTestPhase) phase)
                    .toList();

            if (recentMetaTestPhases.size() >= 2) {
                MetaTestPhase last = recentMetaTestPhases.get(recentMetaTestPhases.size() - 1);
                MetaTestPhase secondLast = recentMetaTestPhases.get(recentMetaTestPhases.size() - 2);

                if (last.getEscapedFaults().equals(secondLast.getEscapedFaults())) {
                    return false;
                }
            }
        }

        return true;
    }
}
