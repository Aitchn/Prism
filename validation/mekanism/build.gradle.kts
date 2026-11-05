plugins {
    `java-library`
}

description = "Validation: Mekanism-style machines, energy, gas pipes and multiblocks rebuilt as a plain Prism mod. Never published."

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
