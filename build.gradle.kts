@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.mia.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.mia.papermc)
    alias(libs.plugins.mia.nms)
    alias(libs.plugins.mia.copyjar)
    alias(libs.plugins.mia.publication)
    alias(libs.plugins.mia.autoversion)
    alias(libs.plugins.compose)
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
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlinx.serialization.kaml)
    compileOnly(libs.kotlinx.coroutines)
    compileOnly(libs.minecraft.mccoroutine)

    // Geary platform
    compileOnly(packyLibs.geary.papermc)
    compileOnly(packyLibs.guiy)

    implementation(libs.bundles.idofront.core)
    implementation(packyLibs.creative.api)
    implementation(packyLibs.creative.serializer.minecraft)
    implementation(packyLibs.creative.server)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xcontext-receivers",
        )
    }
}

tasks {
    shadowJar {
        relocate("team.unnamed", "com.mineinabyss.shaded.unnamed")
    }
}
