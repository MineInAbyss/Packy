rootProject.name = "Packy"

pluginManagement {
    repositories {
        gradlePluginPortal()

        maven("https://repo.mineinabyss.com/releases")
        maven("https://repo.mineinabyss.com/snapshots")
        maven("https://repo.papermc.io/repository/maven-public/") //Paper
    }

    val composeVersion: String by settings
    plugins {
        id("org.jetbrains.compose") version composeVersion
    }
}

dependencyResolutionManagement {
    val idofrontVersion: String by settings

    repositories {
        maven("https://repo.mineinabyss.com/releases")
        maven("https://repo.mineinabyss.com/snapshots")
    }

    versionCatalogs {
        create("libs").from("com.mineinabyss:catalog:$idofrontVersion")
        create("packyLibs").from(files("gradle/packyLibs.versions.toml"))
    }
}

val pluginName: String by settings
