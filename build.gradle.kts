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
