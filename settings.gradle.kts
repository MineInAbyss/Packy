rootProject.name = "packy"

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
        create("idofrontLibs"){
            from("com.mineinabyss:catalog:$idofrontVersion")
            version("mythiccrucible", "2.0.0")
            version("oraxen", "1.171.0-SNAPSHOT")
        }
        create("packyLibs").from(files("gradle/libs.versions.toml"))
    }
}

val pluginName: String by settings
