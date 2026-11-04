plugins {
    `java-library`
}

description = "Platform-neutral Prism runtime"

dependencies {
    api(project(":prism-api"))
    api(project(":prism-platform:spi"))
    compileOnly(libs.paper.api)
}
