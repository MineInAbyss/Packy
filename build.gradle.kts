import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    alias(miaLibs.plugins.mia.kotlin.jvm)
    alias(miaLibs.plugins.kotlinx.serialization)
    alias(miaLibs.plugins.mia.papermc)
    alias(miaLibs.plugins.mia.nms)
    alias(miaLibs.plugins.mia.copyjar)
    alias(miaLibs.plugins.mia.publication)
    alias(miaLibs.plugins.mia.autoversion)
    alias(miaLibs.plugins.compose.compiler)
    id("net.minecrell.plugin-yml.paper") version "0.6.0"
}

repositories {
    mavenCentral()
    maven("https://repo.mineinabyss.com/releases")
    maven("https://repo.mineinabyss.com/snapshots")
    maven("https://repo.mineinabyss.com/mirror")
    mavenLocal()
}

dependencies {
    // MineInAbyss platform
    compileOnly(miaLibs.bundles.idofront.core)
    compileOnly(miaLibs.idofront.nms)
    compileOnly(miaLibs.kotlinx.serialization.json)
    compileOnly(miaLibs.kotlinx.serialization.kaml)
    compileOnly(miaLibs.kotlinx.coroutines)
    compileOnly(miaLibs.minecraft.mccoroutine)

    // Geary platform
    compileOnly(miaLibs.geary.papermc)
    compileOnly(miaLibs.guiy)

    compileOnly(miaLibs.minecraft.plugin.modelengine)
    compileOnly(miaLibs.creative.api)
    compileOnly(miaLibs.creative.serializer.minecraft)
    compileOnly(miaLibs.creative.server)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xcontext-parameters",
        )
    }
}

paper {
    main = "com.mineinabyss.packy.PackyPlugin"
    name = "Packy"
    prefix = "Packy"
    val version: String by project
    this.version = version
    authors = listOf("boy0000")
    apiVersion = "1.21"

    serverDependencies {
        register("Geary") {
            required = true
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("Guiy") {
            required = true
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }

        // LoadTrigger dependencies
        register("ModelEngine") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("MythicCrucible") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
    }
}
