plugins {
    java
    id("net.neoforged.moddev") version "2.0.78"
}

import org.gradle.jvm.tasks.Jar

group = providers.gradleProperty("mod_group").get()
version = providers.gradleProperty("mod_version").get()

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

neoForge {
    version = providers.gradleProperty("neo_version").get()

    parchment {
        minecraftVersion.set(providers.gradleProperty("minecraft_version"))
        mappingsVersion.set("2024.11.17")
    }

    runs {
        create("client") {
            client()
            gameDirectory.set(file("run/client"))
        }
    }

    mods {
        create(providers.gradleProperty("mod_id").get()) {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf(
        "mod_id" to providers.gradleProperty("mod_id").get(),
        "mod_name" to providers.gradleProperty("mod_name").get(),
        "mod_version" to providers.gradleProperty("mod_version").get(),
        "mod_authors" to providers.gradleProperty("mod_authors").get(),
        "mod_description" to providers.gradleProperty("mod_description").get(),
        "minecraft_version" to providers.gradleProperty("minecraft_version").get(),
        "neo_version" to providers.gradleProperty("neo_version").get()
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("EltenaAddon")
}


// Local build outputs only; no deployment or server lifecycle tasks.
tasks.withType<org.gradle.api.tasks.bundling.AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
