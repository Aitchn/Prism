plugins {
    `java-library`
}

description = "Validation: Botania (flora magic) rebuilt as a plain plugin on Prism. Finds API gaps; not a product."

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
