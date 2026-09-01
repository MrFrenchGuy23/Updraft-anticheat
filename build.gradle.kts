import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.updraft"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // Paper 1.21 (Java 21). Provides the Bukkit/Spigot API surface.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // PacketEvents 2.13.0 — packet-level interception (Spigot module includes API + netty-common)
    compileOnly("com.github.retrooper.PacketEvents:packetevents-spigot:2.13.0")

    // LuckPerms 5 API (soft-depend)
    compileOnly("net.luckperms:api:5.4")

    // HikariCP — bundled into the shaded jar
    implementation("com.zaxxer:HikariCP:6.2.1")

    // SQLite JDBC — bundled into the shaded jar (MySQL driver available at runtime from server)
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")

    // Gson — for JSON serialization (Discord webhooks, config helpers).
    // Provided by the server at runtime, so compileOnly is sufficient.
    compileOnly("com.google.code.gson:gson:2.11.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<ProcessResources> {
    filteringCharset = "UTF-8"
    expand("version" to project.version)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    // Relocate HikariCP so we don't conflict with other plugins.
    // slf4j-api is a transitive dependency of HikariCP; it must be relocated too
    // or its classes clash with the slf4j copy the server itself ships.
    relocate("com.zaxxer.hikari", "com.updraft.anticheat.lib.hikari")
    relocate("org.sqlite", "com.updraft.anticheat.lib.sqlite")
    relocate("org.slf4j", "com.updraft.anticheat.lib.slf4j")
    minimize {
        exclude(dependency("com.zaxxer:HikariCP:.*"))
        exclude(dependency("org.xerial:sqlite-jdbc:.*"))
    }
}

tasks.named("build") {
    dependsOn("shadowJar")
}
