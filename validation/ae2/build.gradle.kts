plugins {
    `java-library`
}

description = "Validation: Applied Energistics 2 rebuilt as a plain plugin on Prism. Never published; see docs/validation/ae2.md."

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
