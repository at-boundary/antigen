package io.antigen.orchestrator;

import io.antigen.llm.ClaudeGenerator;
import io.antigen.model.GenerationResult;
import io.antigen.phases.BuildPhase;
import io.antigen.phases.GenerationPhase;
import io.antigen.phases.MetaTestPhase;
import io.antigen.phases.TestPhase;
import io.antigen.runners.GradleRunner;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;

@Slf4j
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
        log.info("=== Starting Antigen Test Generation ===");
        log.info("API Spec: {}", specPath);
        log.info("Project: {}", projectPath);
        log.info("Max Retries: {}", config.getMaxRetries());

        if (!claudeGenerator.isClaudeAvailable()) {
            log.error("Claude CLI is not available. Please install Claude Code and ensure 'claude' command is in PATH.");
            return GenerationResult.failure(0, "Claude CLI not found. Install Claude Code first.");
        }

        GenerationContext context = GenerationContext.builder()
                .specPath(specPath)
                .projectPath(projectPath)
                .requirements(requirements)
                .build();

        for (int attempt = 1; attempt <= config.getMaxRetries(); attempt++) {
            log.info("");
            log.info("=== Attempt {}/{} ===", attempt, config.getMaxRetries());

            log.info("State 1: Generating tests with Claude...");
            GenerationPhase genPhase = claudeGenerator.generate(context);
            log.info("Result: {}", genPhase.isSuccess() ? "SUCCESS" : "FAILED");

            if (genPhase.failed()) {
                log.warn("Generation failed: {}", genPhase.getFeedback());
                context = context.addFeedback(genPhase);
                continue;
            }

            log.info("State 2: Building project...");
            BuildPhase buildPhase = gradleRunner.build(context);
            log.info("Result: {}", buildPhase.isSuccess() ? "SUCCESS" : "FAILED");

            if (buildPhase.failed()) {
                log.warn("Build failed with {} errors", buildPhase.getCompilationErrors().size());
                context = context.addFeedback(buildPhase);
                continue;
            }

            log.info("State 3: Running tests (without MetaTest)...");
            TestPhase testPhase = gradleRunner.runTests(context);
            log.info("Result: {}", testPhase.isSuccess() ? "SUCCESS" : "FAILED");

            if (testPhase.failed()) {
                log.warn("Tests failed: {} failures", testPhase.getTestFailures().size());
                context = context.addFeedback(testPhase);
                continue;
            }

            log.info("State 4: Running tests with MetaTest fault injection...");
            MetaTestPhase metaTestPhase = gradleRunner.runMetaTest(context);
            log.info("Result: {}", metaTestPhase.isSuccess() ? "SUCCESS" : "FAILED");
            log.info("Fault Detection Rate: {:.1f}%", metaTestPhase.getFaultDetectionRate() * 100);

            if (metaTestPhase.hasEscapedFaults()) {
                log.warn("MetaTest failed: {} faults escaped", metaTestPhase.getEscapedFaults().size());

                if (shouldRetry(context, attempt)) {
                    context = context.addFeedback(metaTestPhase);
                    continue;
                } else {
                    log.warn("Same MetaTest failures repeating, stopping retries");
                    return GenerationResult.failure(attempt,
                            "MetaTest failures are repeating. Generated tests may be at maximum quality.");
                }
            }

            log.info("=== SUCCESS ===");
            log.info("Tests generated and validated in {} attempts", attempt);
            return GenerationResult.success(attempt, genPhase.getGeneratedFiles());
        }

        log.error("=== FAILURE ===");
        log.error("Failed to generate valid tests after {} attempts", config.getMaxRetries());
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
