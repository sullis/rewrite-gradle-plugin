import nl.javadude.gradle.plugins.license.LicenseExtension
import org.gradle.rewrite.build.GradleVersionData
import org.gradle.rewrite.build.GradleVersionsCommandLineArgumentProvider
import java.util.*

plugins {
    java
    groovy
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version("0.12.0")
    id("io.spring.release") version ("0.20.1") apply (false)
}

apply(plugin = "license")

pluginBundle {
    website = "https://github.com/openrewrite/rewrite-gradle-plugin"
    vcsUrl = "https://github.com/openrewrite/rewrite-gradle-plugin.git"
    tags = listOf("rewrite", "refactoring", "java", "checkstyle")
}

gradlePlugin {
    plugins {
        create("rewriteMetrics") {
            id = "org.openrewrite.rewrite-metrics"
            displayName = "Rewrite metrics publishing"
            description = "Publish metrics about refactoring operations happening across your organization."
            implementationClass = "org.openrewrite.gradle.RewriteMetricsPlugin"
        }

        create("rewrite") {
            id = "org.openrewrite.rewrite"
            displayName = "Rewrite"
            description = "Automatically eliminate technical debt"
            implementationClass = "org.openrewrite.gradle.RewritePlugin"
        }
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, TimeUnit.SECONDS)
        cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.named<JavaCompile>("compileJava") {
    options.isFork = true
    options.forkOptions.executable = "javac"
    options.compilerArgs.addAll(listOf("--release", "8"))
}

val plugin: Configuration by configurations.creating

configurations.getByName("compileOnly").extendsFrom(plugin)

// Fixed version numbers because com.gradle.plugin-publish will publish poms with requested rather than resolved versions
val rewriteVersion = "7.0.0-rc.5"
val prometheusVersion = "1.3.0"
val nettyVersion = "1.1.0"
dependencies {
    plugin("org.openrewrite:rewrite-java:$rewriteVersion")
    plugin("io.micrometer.prometheus:prometheus-rsocket-client:$prometheusVersion")
    plugin("io.rsocket:rsocket-transport-netty:$nettyVersion")

    implementation("org.openrewrite:rewrite-java-11:$rewriteVersion")
    implementation("org.openrewrite:rewrite-java-8:$rewriteVersion")
    implementation("org.openrewrite:rewrite-xml:$rewriteVersion")
    implementation("org.openrewrite:rewrite-maven:$rewriteVersion")
    implementation("org.openrewrite:rewrite-properties:$rewriteVersion")
    implementation("org.openrewrite:rewrite-yaml:$rewriteVersion")
    api("org.openrewrite:rewrite-java:$rewriteVersion")
    api("io.micrometer.prometheus:prometheus-rsocket-client:$prometheusVersion")
    api("io.rsocket:rsocket-transport-netty:$nettyVersion")

    testImplementation(gradleTestKit())
    testImplementation("org.codehaus.groovy:groovy-all:2.5.10")
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.5")
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(plugin)
}

project.rootProject.tasks.getByName("postRelease").dependsOn(project.tasks.getByName("publishPlugins"))

tasks.named<Test>("test") {
    systemProperty(
            GradleVersionsCommandLineArgumentProvider.PROPERTY_NAME,
            project.findProperty("testedGradleVersion") ?: gradle.gradleVersion
    )
}

tasks.register<Test>("testGradleReleases") {
    jvmArgumentProviders.add(GradleVersionsCommandLineArgumentProvider(GradleVersionData::getReleasedVersions))
}

tasks.register<Test>("testGradleNightlies") {
    jvmArgumentProviders.add(GradleVersionsCommandLineArgumentProvider(GradleVersionData::getNightlyVersions))
}

configure<LicenseExtension> {
    ext.set("year", Calendar.getInstance().get(Calendar.YEAR))
    skipExistingHeaders = true
    header = project.rootProject.file("gradle/licenseHeader.txt")
    mapping(mapOf("kt" to "SLASHSTAR_STYLE", "java" to "SLASHSTAR_STYLE"))
    strictCheck = true
}
