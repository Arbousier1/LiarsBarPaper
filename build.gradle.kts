plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.5.0"
}

group = "com.jijifujiji"
version = "1.0.0"
description = "MC 版骗子酒馆 Paper 插件"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    paperweight.paperDevBundle("1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
}

// === CraftEngine bundle generation ===
val craftengineBundleDir = layout.buildDirectory.dir("generated/craftengine")
val craftengineSourceDir = layout.projectDirectory.dir("src/main/craftengine")
val resourcepackSourceDir = layout.projectDirectory.dir("resourcepack")

val generateCraftEngineBundle by tasks.registering {
    group = "craftengine"
    description = "Generate CraftEngine bundle for resource pack distribution"

    inputs.dir(craftengineSourceDir)
    inputs.dir(resourcepackSourceDir)
    outputs.dir(craftengineBundleDir)

    doLast {
        val outputRoot = craftengineBundleDir.get().asFile.resolve("liarsbar")
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()

        // Copy pack.yml with version substitution
        val packYml = craftengineSourceDir.file("pack.yml").asFile
        val packContent = packYml.readText(Charsets.UTF_8)
            .replace("\${projectVersion}", project.version.toString())
        outputRoot.resolve("pack.yml").writeText(packContent, Charsets.UTF_8)

        // Copy configuration
        val configSrc = craftengineSourceDir.dir("configuration").asFile
        if (configSrc.exists()) {
            configSrc.copyRecursively(outputRoot.resolve("configuration"), overwrite = true)
        }

        // Copy resourcepack assets
        val rpAssets = resourcepackSourceDir.dir("assets").asFile
        if (rpAssets.exists()) {
            rpAssets.copyRecursively(outputRoot.resolve("resourcepack").resolve("assets"), overwrite = true)
        }

        // Generate bundle index
        val bundleFiles = outputRoot.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(outputRoot).invariantSeparatorsPath }
            .sorted()
            .toList()
        outputRoot.resolve("_bundle_index.txt")
            .writeText(bundleFiles.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    processResources {
        dependsOn(generateCraftEngineBundle)
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
        from(craftengineBundleDir) {
            into("craftengine")
        }
    }

    jar {
        archiveBaseName.set("LiarsBarPaper")
    }

    assemble {
        dependsOn(reobfJar)
    }
}