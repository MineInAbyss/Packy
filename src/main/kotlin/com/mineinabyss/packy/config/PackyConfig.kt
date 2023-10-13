package com.mineinabyss.packy.config

import com.mineinabyss.packy.helpers.PackyServer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.Serializable

@Serializable
data class PackyConfig(
    @EncodeDefault(ALWAYS) val mcmeta: PackyMcMeta = PackyMcMeta(),
    @EncodeDefault(ALWAYS) val icon: String = "pack.png",
    @EncodeDefault(ALWAYS) val server: PackyServer = PackyServer(),
    @EncodeDefault(ALWAYS) val zipDestination: String = "built-packs",
    @EncodeDefault(ALWAYS) val prompt: String = "",
    @EncodeDefault(ALWAYS) val force: Boolean = false
) {
    @Serializable
    data class PackyMcMeta(val format: Int = 15, val description: String = "Packy Resourcepack")
    @Serializable
    data class PackyServer(val ip: String = "127.0.0.1", val port: Int = 8080)
}
