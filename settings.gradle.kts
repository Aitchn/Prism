rootProject.name = "prism"

include(
    "prism-api",
    "prism-core",
    "prism-platform:spi",
    "prism-platform:paper",
    "prism-platform:folia",
    "prism-client",
    "prism-model",
    "prism-bedrock",
    "prism-pack",
    "prism-plugin",
    "prism-testkit",
    "examples:example-mod",
)

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://repo.codemc.io/repository/maven-releases/") { name = "codemc" }
        maven("https://repo.opencollab.dev/main/") { name = "opencollab" }
    }
}
