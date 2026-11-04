plugins {
    `java-library`
}

// Public API: the only artifact mod authors compile against.
// Must not expose PacketEvents, BetterModel or Folia types (enforced by prism-plugin's ArchitectureTest).
description = "Prism public API"

dependencies {
    compileOnly(libs.paper.api)
    testImplementation(libs.paper.api)
}
