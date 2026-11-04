plugins {
    `java-library`
}

description = "Platform-neutral Prism runtime"

dependencies {
    api(project(":prism-api"))
    api(project(":prism-platform:spi"))
    compileOnly(libs.paper.api)
    // SnakeYAML ships inside every Bukkit-family server, so it is provided at runtime and not shaded.
    compileOnly(libs.snakeyaml)
    testImplementation(libs.snakeyaml)
    testImplementation(libs.paper.api)
}
