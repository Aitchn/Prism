plugins {
    `java-library`
}

description = "Minimal Prism mod: one item with a bound behavior. Content lives in src/main/resources/prism/."

dependencies {
    compileOnly(project(":prism-api"))
    compileOnly(libs.paper.api)

    testImplementation(project(":prism-testkit"))
    testImplementation(libs.paper.api)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
