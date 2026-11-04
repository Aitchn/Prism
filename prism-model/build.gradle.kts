plugins {
    `java-library`
}

description = "Blockbench model bridge. BetterModel types never leave this module."

dependencies {
    api(project(":prism-core"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bettermodel.api)
}
