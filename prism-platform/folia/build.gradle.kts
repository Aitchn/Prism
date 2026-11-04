plugins {
    `java-library`
}

description = "Folia platform implementation"

dependencies {
    api(project(":prism-platform:spi"))
    compileOnly(libs.paper.api)
}
