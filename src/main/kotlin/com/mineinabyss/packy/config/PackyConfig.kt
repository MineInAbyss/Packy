package com.mineinabyss.packy.config

import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.at
import com.mineinabyss.guiy.modifiers.size
import com.mineinabyss.idofront.serialization.SerializableItemStack
import com.mineinabyss.packy.helpers.PackyServer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.path.div

@Serializable
data class PackyConfig(
    @EncodeDefault(ALWAYS) val mcmeta: PackyMcMeta = PackyMcMeta(),
    @EncodeDefault(ALWAYS) val icon: String = "pack.png",
    @EncodeDefault(ALWAYS) val server: PackyServer = PackyServer(),
    @SerialName("zipDestination") @EncodeDefault(ALWAYS) val _zipDestination: String = "builtPacks/",
    @EncodeDefault(ALWAYS) val prompt: String = "",
    @EncodeDefault(ALWAYS) val force: Boolean = false,
    @EncodeDefault(ALWAYS) val menu: PackyMenu = PackyMenu()
) {
    val zipDestination get() = packy.plugin.dataFolder.toPath() / _zipDestination
    @Serializable data class PackyMcMeta(val format: Int = 15, val description: String = "Packy Resourcepack")
    @Serializable data class PackyServer(val ip: String = "127.0.0.1", val port: Int = 8080) {
        fun url(hash: String) = "http://$ip:$port/$hash.zip"
    }

    @Serializable
    data class PackyMenu(
        val title: String = "Packy Pack Picker",
        val height: Int = 6,
        val subMenus: Map<String, PackySubMenu> = mapOf()
    )

    @Serializable data class PackySubMenu(val title: String = "Packy SubMenu", val height: Int = 6, val button: SerializableItemStack, val modifiers: Modifiers = Modifiers(), val packs: Map<String, PackyPack> = mapOf())
    @Serializable data class PackyPack(val button: SerializableItemStack, val modifiers: Modifiers)
    @Serializable data class Modifiers(val offset: Offset = Offset(), val size: Size = Size()) {
        fun toModifier(): Modifier = Modifier.at(offset.x, offset.y).size(size.width, size.height)
    }
    @Serializable data class Offset(val x: Int = 0, val y: Int = 0) {
        fun toAtModifier(modifier: Modifier = Modifier): Modifier = modifier.at(x, y)
    }
    @Serializable data class Size(val width: Int = 1, val height: Int = 1) {
        fun toSizeModifier(modifier: Modifier = Modifier): Modifier = modifier.size(width, height)
    }
}
