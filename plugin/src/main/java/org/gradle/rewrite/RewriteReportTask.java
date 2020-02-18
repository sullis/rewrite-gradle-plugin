package org.gradle.rewrite;

import com.netflix.rewrite.refactor.RefactorResult;
import org.gradle.api.tasks.TaskAction;

import java.util.List;

public class RewriteReportTask extends AbstractRewriteTask {
    @TaskAction
    public void reportOnAvailableRefactorings() {
        StyledTextService textOutput = new StyledTextService(getServices());

        List<RefactorResult> results = refactor();

        if (!results.isEmpty()) {
            textOutput.withStyle(Styling.Red).text("\u2716 Your source code requires refactoring. ");
            textOutput.text("Run").withStyle(Styling.Bold).text("./gradlew fixSourceLint");
            textOutput.println(" to automatically fix.");

            for (RefactorResult result : results) {
                textOutput.withStyle(Styling.Bold).println(result.getOriginal().getSourcePath());
                result.getRulesThatMadeChanges().stream()
                        .sorted()
                        .forEach(rule -> textOutput.println("  " + rule));
            }
        }
    }
}