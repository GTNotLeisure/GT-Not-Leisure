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
                private DependencyVersions() {}
                public static final String GTNLLIB = "required-after:gtnllib@[$version,);";
            }
            """.trimIndent() + "\n"
        )
    }
}
sourceSets.main { java.srcDir(generateDependencyVersions) }

minecraft {
    extraRunJvmArguments.addAll("-Xmx8G", "-Xms8G", "-Dgtnhlib.dumpkeys=true")
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
