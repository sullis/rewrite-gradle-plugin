/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;

/**
 * Adds the RewriteExtension to the current project and registers tasks per-sourceSet that implement rewrite fixing and
 * warning. Only needs to be applied to projects with java sources. No point in applying this to any project that does
 * not have java sources of its own, such as the root project in a multi-project builds.
 */
public class RewritePlugin implements Plugin<Project> {

    /*
     Note on compatibility:
     Since we're in the software modernization and improvement business we want to support old versions of Gradle.
     As written this project doesn't use any APIs not present as of Gradle 4.7.
     That predates Gradle supporting Java 11, which came in Gradle 5.0.
     So our automated tests wont currently _enforce_ this compatibility guarantee.
     Until that changes, tread carefully and test manually if you introduce any new usage of any Gradle API.
     */

    @Override
    public void apply(Project project) {
        RewriteExtension maybeExtension = project.getExtensions().findByType(RewriteExtension.class);
        if(maybeExtension == null) {
            maybeExtension = project.getExtensions().create("rewrite", RewriteExtension.class, project);
            maybeExtension.setToolVersion("2.x");
        }
        final RewriteExtension extension = maybeExtension;
        TaskContainer tasks = project.getTasks();

        JavaPluginConvention javaPlugin = project.getConvention().getPlugin(JavaPluginConvention.class);
        SourceSetContainer sourceSets = javaPlugin.getSourceSets();

        // Fix is meant to be invoked manually and so is not made a dependency of any existing task
        Task rewriteFixAll = tasks.create("rewriteFix",
                taskClosure(task -> {
                    task.setGroup("rewrite");
                    task.setDescription("Apply the active refactoring recipes to all sources");
                })
        );

        // Warn hooks into the regular Java build by becoming a dependency of "check", the same way that checkstyle or unit tests do
        Task rewriteWarnAll = tasks.create("rewriteWarn", taskClosure(task -> {
                    task.setGroup("rewrite");
                    task.setDescription("Dry run the active refactoring recipes to all sources. No changes will be made.");
                })
        );

        Task checkTask = tasks.getByName("check");
        checkTask.dependsOn(rewriteWarnAll);

        Task rewriteDiscoverAll = tasks.create("rewriteDiscover", taskClosure(task -> {
            task.setGroup("rewrite");
            task.setDescription("Lists all available recipes and their visitors available to each SourceSet");
        }));

        sourceSets.all(sourceSet -> {
            String rewriteFixTaskName = "rewriteFix" + sourceSet.getName().substring(0, 1).toUpperCase() + sourceSet.getName().substring(1);

            RewriteFixTask rewriteFix = tasks.create(rewriteFixTaskName, RewriteFixTask.class, sourceSet, extension);
            rewriteFixAll.configure(taskClosure(it -> it.dependsOn(rewriteFix)));

            String rewriteDiscoverTaskName = "rewriteDiscover" + sourceSet.getName().substring(0, 1).toUpperCase() + sourceSet.getName().substring(1);
            RewriteDiscoverTask discoverTask = tasks.create(rewriteDiscoverTaskName, RewriteDiscoverTask.class, sourceSet, extension);
            rewriteDiscoverAll.dependsOn(discoverTask);

            String compileTaskName = sourceSet.getCompileTaskName("java");
            Task compileTask = tasks.getByName(compileTaskName);
            compileTask.configure(taskClosure(it -> it.mustRunAfter(rewriteFix)));

            String rewriteWarnTaskName = "rewriteWarn" + sourceSet.getName().substring(0, 1).toUpperCase() + sourceSet.getName().substring(1);
            RewriteWarnTask rewriteWarn = tasks.create(rewriteWarnTaskName, RewriteWarnTask.class, sourceSet, extension);
            rewriteWarnAll.configure(taskClosure(it -> it.dependsOn(rewriteWarn)));
        });
    }

    private Closure<Task> taskClosure(Action<Task> configFun) {
        return new Closure<Task>(this) {
            public void doCall(Task arg) {
                configFun.execute(arg);
            }
        };
    }

//    void configureMetrics(RewriteTask task) {
//        Project project = task.getProject();
//
//        RewriteMetricsPlugin metricsPlugin = project.getRootProject().getPlugins().findPlugin(RewriteMetricsPlugin.class);
//
//        getMeterRegistry().config()
//                .commonTags(
//                        "project.name", project.getName(),
//                        "project.display.name", project.getDisplayName(),
//                        "project.path", project.getPath(),
//                        "project.root.project.name", project.getRootProject().getName(),
//                        "gradle.version", project.getGradle().getGradleVersion(),
//                        "rewrite.plan.name", getRewritePlanName()
//                )
//                .commonTags(metricsPlugin != null ? metricsPlugin.getExtraTags() : Tags.empty())
//                .meterFilter(new MeterFilter() {
//                    @Override
//                    public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
//                        if (id.getName().equals("rewrite.parse")) {
//                            return DistributionStatisticConfig.builder()
//                                    .percentilesHistogram(true)
//                                    .maximumExpectedValue((double) Duration.ofMillis(250).toNanos())
//                                    .build()
//                                    .merge(config);
//                        }
//                        return config;
//                    }
//                });
//
//        task.setMeterRegistry(getMeterRegistry());
//    }
//
//    String getRewritePlanName();
//
//    PrometheusMeterRegistry getMeterRegistry();
}
