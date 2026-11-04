plugins {
    `java-library`
}

description = "Prism pack module"

dependencies {
    api(project(":prism-core"))
    compileOnly(libs.paper.api)
}
