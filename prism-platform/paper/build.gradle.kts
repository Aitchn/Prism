plugins {
    `java-library`
}

description = "Paper platform implementation"

dependencies {
    api(project(":prism-platform:spi"))
    compileOnly(libs.paper.api)
}
