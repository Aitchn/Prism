import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    base
    alias(libs.plugins.shadow) apply false
}

// Shared conventions for every module that applies `java-library`.
subprojects {
    plugins.withId("java-library") {
        group = rootProject.group
        version = rootProject.version
        // :prism-platform:spi -> prism-platform-spi, so artifact names never collide.
        extensions.configure<BasePluginExtension> {
            archivesName = path.removePrefix(":").replace(':', '-')
        }

        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(25)
            withSourcesJar()
        }

        // Only the modules creators compile against are published: the API, the runtime core, the platform SPI that core exposes, and the test kit.
        if (path in setOf(":prism-api", ":prism-core", ":prism-platform:spi", ":prism-testkit")) {
            apply(plugin = "maven-publish")
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("maven") {
                        from(components["java"])
                        pom {
                            name = project.name
                            description = project.description
                            url = "https://github.com/Aitchn/Prism"
                            licenses {
                                license {
                                    name = "MIT"
                                    url = "https://opensource.org/licenses/MIT"
                                }
                            }
                            scm { url = "https://github.com/Aitchn/Prism" }
                        }
                    }
                }
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release = 25
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Werror"))
        }

        dependencies {
            add("compileOnly", rootProject.libs.jetbrains.annotations)
            add("testImplementation", platform(rootProject.libs.junit.bom))
            add("testImplementation", rootProject.libs.junit.jupiter)
            add("testRuntimeOnly", rootProject.libs.junit.launcher)
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("failed", "skipped")
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
}
