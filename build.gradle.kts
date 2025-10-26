plugins {
    kotlin("jvm") version "2.3.0-Beta1"
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "3.0.1"
}

group = "io.aitchn.prism"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.jeff-media:custom-block-data:2.2.5")
}

tasks {
    runServer {
        minecraftVersion("1.21.8")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.shadowJar {
    relocate("com.jeff_media.customblockdata", "io.aitchn.prism.lib.customblockdata")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
