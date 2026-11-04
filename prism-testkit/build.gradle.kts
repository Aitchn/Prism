plugins {
    `java-library`
}

description = "Test helpers for mod authors (published to Maven)"

dependencies {
    api(project(":prism-api"))
    api(project(":prism-core"))
    // The server provides SnakeYAML and the Bukkit API at runtime; tests outside a server need them on the classpath.
    api(libs.snakeyaml)
    compileOnly(libs.paper.api)
}
