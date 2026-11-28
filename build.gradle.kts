import java.util.Properties

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val gtnllibVersion = providers.gradleProperty("gtnllibVersion")
val dependencySources = layout.buildDirectory.dir("generated/sources/gtnlDependencies/java")
val generateDependencyVersions = tasks.register("generateDependencyVersions") {
    inputs.property("gtnllibVersion", gtnllibVersion)
    outputs.dir(dependencySources)
    doLast {
        val version = inputs.properties.getValue("gtnllibVersion").toString()
        require(version.matches(Regex("[0-9]+(\\.[0-9]+)*"))) { "Invalid GTNLLib version: $version" }
        val output = outputs.files.singleFile.resolve("com/science/gtnl/DependencyVersions.java")
        output.parentFile.mkdirs()
        output.writeText(
            """
            package com.science.gtnl;

            public final class DependencyVersions {

                public static final String GTNLLIB = "required-after:gtnllib@[$version,);";

                private DependencyVersions() {}
            }
            """.trimIndent() + "\n"
        )
    }
}
sourceSets.main { java.srcDir(generateDependencyVersions) }

fun gitIdentity(vararg arguments: String) = providers.exec {
    commandLine("git", *arguments)
}.standardOutput.asText.map { it.trim() }

val updateIdentity = mapOf(
    "repository" to providers.environmentVariable("GTNL_BUILD_REPOSITORY")
        .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
        .orElse("GTNotLeisure/GT-Not-Leisure"),
    "branch" to providers.environmentVariable("GTNL_BUILD_BRANCH").orElse(
        providers.environmentVariable("GTNL_BUILD_CHANNEL").orElse("local").flatMap {
            if (it == "local") providers.provider { "" } else gitIdentity("branch", "--show-current")
        }
    ),
    "commit" to providers.environmentVariable("GTNL_BUILD_COMMIT").orElse(
        providers.environmentVariable("GTNL_BUILD_CHANNEL").orElse(
            if (version.toString().matches(Regex("[vV]?[0-9]+(\\.[0-9]+)*(-hotfix[0-9]+)?"))) "stable" else "local"
        ).flatMap {
            if (it == "local") providers.provider { "" } else gitIdentity("rev-parse", "HEAD")
        }
    ),
    "channel" to providers.environmentVariable("GTNL_BUILD_CHANNEL").orElse(
        if (version.toString().matches(Regex("[vV]?[0-9]+(\\.[0-9]+)*(-hotfix[0-9]+)?"))) "stable" else "local"
    ),
    "version" to providers.provider { project.version.toString() },
    "releaseRange" to providers.gradleProperty("gtnlUpdateReleaseRange")
)
val updateResources = layout.buildDirectory.dir("generated/resources/updateIdentity")
val generateUpdateIdentity = tasks.register("generateUpdateIdentity") {
    updateIdentity.forEach { (key, value) -> inputs.property(key, value) }
    outputs.dir(updateResources)
    doLast {
        val properties = Properties()
        inputs.properties.forEach { (key, value) -> properties.setProperty(key, value.toString()) }
        val output = outputs.files.singleFile.resolve("gtnl-build.properties")
        output.parentFile.mkdirs()
        output.outputStream().use { properties.store(it, "GTNL build identity") }
    }
}
tasks.named<ProcessResources>("processResources") { from(generateUpdateIdentity) }
tasks.named<Jar>("jar") {
    from(files("LICENSE.txt", "LICENSE-ADDITIONAL-TERMS.md", "ADDITIONAL_TERMS.zh_CN.md")) { into("META-INF") }
}

minecraft {
    extraRunJvmArguments.addAll("-Xmx8G", "-Xms8G")
}

tasks.withType<JavaCompile>().configureEach {
    options.annotationProcessorPath = configurations.annotationProcessor.get()
}

sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
}

val runConfigs = listOf(
    "runClient" to "run/client",
    "runClient17" to "run/client_new",
    "runClient21" to "run/client_new",
    "runClient25" to "run/client_new",
    "runServer" to "run/server",
    "runServer17" to "run/server_new",
    "runServer21" to "run/server_new",
    "runServer25" to "run/server_new"
)

runConfigs.forEach { (taskName, path) ->
    tasks.named<JavaExec>(taskName) {
        workingDir = file("${projectDir}/$path")
        doFirst {
            workingDir.mkdirs()
        }
    }
}
