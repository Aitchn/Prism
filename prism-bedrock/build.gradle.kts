plugins {
    `java-library`
}

description = "Prism bedrock module"

dependencies {
    api(project(":prism-core"))
    compileOnly(libs.paper.api)
}
