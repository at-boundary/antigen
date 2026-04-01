package io.antigen.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class AntigenPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().register("generateAITests", GenerateAITestsTask.class, task -> {
            task.setGroup("antigen");
            task.setDescription("Generate AI-powered tests from API specification with MetaTest validation");
        });
    }
}
