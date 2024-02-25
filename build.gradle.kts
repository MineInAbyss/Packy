import net.minecrell.pluginyml.paper.PaperPluginDescription

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(idofrontLibs.plugins.mia.kotlin.jvm)
    alias(idofrontLibs.plugins.kotlinx.serialization)
    alias(idofrontLibs.plugins.mia.papermc)
    alias(idofrontLibs.plugins.mia.nms)
    alias(idofrontLibs.plugins.mia.copyjar)
    alias(idofrontLibs.plugins.mia.publication)
    alias(idofrontLibs.plugins.mia.autoversion)
    alias(idofrontLibs.plugins.compose)
    id("net.minecrell.plugin-yml.paper") version "0.6.0"
}

repositories {
    mavenCentral()
    maven("https://repo.mineinabyss.com/releases")
    maven("https://repo.mineinabyss.com/snapshots")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://repo.unnamed.team/repository/unnamed-public/")
    maven("https://mvn.lumine.io/repository/maven-public/")
    mavenLocal()
}

dependencies {
    // MineInAbyss platform
    compileOnly(idofrontLibs.bundles.idofront.core)
    compileOnly(idofrontLibs.kotlinx.serialization.json)
    compileOnly(idofrontLibs.kotlinx.serialization.kaml)
    compileOnly(idofrontLibs.kotlinx.coroutines)
    compileOnly(idofrontLibs.minecraft.mccoroutine)

    // Geary platform
    compileOnly(packyLibs.geary.papermc)
    compileOnly(packyLibs.guiy)

    compileOnly(idofrontLibs.creative.api)
    compileOnly(idofrontLibs.creative.serializer.minecraft)
    compileOnly(idofrontLibs.creative.server)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xcontext-receivers",
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
    apiVersion = "1.20"

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
    }
}
