plugins {
    // Literales obligatorios -- el bloque plugins{} de Gradle se resuelve
    // antes que el resto del script, no puede leer gradle.properties aca.
    id("net.fabricmc.fabric-loom") version "1.17.13"
    kotlin("jvm") version "2.4.0" // debe coincidir con el Kotlin que bundlea FLK (ver gradle.properties)
}

// CI (mod-build.yml) compila con -Phd_version=<hash-corto-del-commit> para builds
// continuos por commit; sin ese flag (build local), se usa mod_version de gradle.properties.
version = (findProperty("hd_version") as String?) ?: (property("mod_version") as String)
group = property("maven_group") as String

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    // Toolchain 26.1+: sin mappings(...) -- Mojang ya distribuye nombres oficiales sin ofuscar.
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("flk_version")}")
    // NO agregar kotlin-stdlib aparte -- fabric-language-kotlin ya lo bundlea.
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}
