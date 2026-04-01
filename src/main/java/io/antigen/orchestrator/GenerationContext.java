package io.antigen.orchestrator;

import io.antigen.phases.PhaseResult;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.nio.file.Path;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class GenerationContext {
    Path specPath;
    Path projectPath;
    Path promptTemplatePath;
    @Singular
    List<String> requirements;
    @Singular("feedback")
    List<PhaseResult> feedbackHistory;

    public boolean hasFeedback() {
        return feedbackHistory != null && !feedbackHistory.isEmpty();
    }

    public PhaseResult getLatestFeedback() {
        if (feedbackHistory == null || feedbackHistory.isEmpty()) {
            return null;
        }
        return feedbackHistory.get(feedbackHistory.size() - 1);
    }

    public GenerationContext addFeedback(PhaseResult feedback) {
        return this.toBuilder()
                .feedback(feedback)
                .build();
    }

    public List<PhaseResult> getLastN(int n) {
        if (feedbackHistory == null || feedbackHistory.isEmpty()) {
            return List.of();
        }
        int size = feedbackHistory.size();
        int fromIndex = Math.max(0, size - n);
        return feedbackHistory.subList(fromIndex, size);
    }
}
