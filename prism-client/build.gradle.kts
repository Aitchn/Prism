plugins {
    `java-library`
}

description = "Client presentation (packet rewriting). PacketEvents types never leave this module."

dependencies {
    api(project(":prism-core"))
    compileOnly(libs.paper.api)
    compileOnly(libs.packetevents.spigot)
}
