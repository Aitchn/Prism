plugins {
    `java-library`
}

description = "Prism bedrock module: Geyser registration of synthetic block states"

dependencies {
    api(project(":prism-core"))
    compileOnly(libs.paper.api)
    // Provided by the Geyser plugin at runtime; the registrar is only loaded when Geyser is installed.
    compileOnly(libs.geyser.api)
}

tasks.withType<JavaCompile>().configureEach {
    // The Geyser API annotates with checker-framework types that are not on the compile classpath; javac would warn
    // about each of them (and warnings are errors).
    options.compilerArgs.add("-Xlint:-classfile")
}
