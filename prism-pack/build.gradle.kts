plugins {
    `java-library`
}

description = "Resource pack compiler: Java pack, Bedrock pack and mappings"

dependencies {
    api(project(":prism-core"))
    compileOnly(libs.paper.api)
    // SnakeYAML (JSON is valid YAML) ships inside every Bukkit-family server.
    compileOnly(libs.snakeyaml)
    testImplementation(libs.snakeyaml)
}
