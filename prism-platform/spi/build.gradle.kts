plugins {
    `java-library`
}

description = "Internal platform SPI (scheduling, platform detection)"

dependencies {
    api(project(":prism-api"))
    compileOnly(libs.paper.api)
}
