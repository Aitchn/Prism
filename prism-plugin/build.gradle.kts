plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

description = "The Prism.jar Bukkit plugin: assembles every module into one jar"

dependencies {
    implementation(project(":prism-core"))
    implementation(project(":prism-platform:spi"))
    implementation(project(":prism-platform:paper"))
    implementation(project(":prism-platform:folia"))
    implementation(project(":prism-client"))
    implementation(project(":prism-model"))
    implementation(project(":prism-bedrock"))
    implementation(project(":prism-pack"))
    compileOnly(libs.paper.api)

    testImplementation(libs.archunit.junit5)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName = "Prism"
    archiveClassifier = ""
    // The plain jar is not a deliverable; only the shaded Prism.jar is.
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
