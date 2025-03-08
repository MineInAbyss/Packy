package com.mineinabyss.packy.config

import com.charleskorn.kaml.YamlComment
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.placement.absolute.at
import com.mineinabyss.guiy.modifiers.size
import com.mineinabyss.idofront.serialization.DurationSerializer
import com.mineinabyss.idofront.serialization.SerializableItemStack
import com.mineinabyss.idofront.serialization.toSerializable
import com.mineinabyss.packy.helpers.TemplateIds
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import net.kyori.adventure.resource.ResourcePackStatus
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PackyConfig(
    @EncodeDefault(ALWAYS) val mcmeta: PackyMcMeta = PackyMcMeta(),
    @EncodeDefault(ALWAYS) val icon: String = "pack.png",
    @EncodeDefault(ALWAYS) val server: PackyServer = PackyServer(),
    @EncodeDefault(ALWAYS) val prompt: String? = null,
    @EncodeDefault(ALWAYS) val force: Boolean = false,
    @EncodeDefault(ALWAYS) val sendOnReload: Boolean = false,
    @YamlComment("What ObfuscationType to use, valid options are FULL, SIMPLE & NONE")
    @EncodeDefault(ALWAYS) val obfuscation: Obfuscation = Obfuscation(),
    @YamlComment("This will use PackSquash to automatically squash all templates")
    @EncodeDefault(ALWAYS) val packSquash: PackSquash = PackSquash(),
    @YamlComment(
        "The amount of TemplateID combinations Packy should cache the ResourcePack off",
        "If your ResourcePack is large it is recommended to lower this value"
    )
    @EncodeDefault(ALWAYS) val cachedPackAmount: Int = 10,
    @EncodeDefault(ALWAYS) val menu: PackyMenu = PackyMenu()
) {

    @Serializable
    data class Obfuscation(val type: Type = Type.NONE, val cache: Boolean = true) {
        enum class Type {
            FULL, SIMPLE, NONE
        }
    }



    @Serializable
    data class PackSquash(
        val enabled: Boolean = false,
        @YamlComment("Path to the PackSquash executable")
        val exePath: String = Bukkit.getServer().pluginsFolder.resolve("Packy/packsquash").absolutePath.replace("\\", "/"),
        @YamlComment("Path to the settings file for PackSquash")
        val settingsPath: String = "packsquash.toml"
    )
    @Serializable
    data class PackyMcMeta(val format: Int = 15, val description: String = "Packy Resourcepack")

    @Serializable
    data class PackyServer(
        @YamlComment("Change this to your server's actual IP, unless on localhost")
        val ip: String = "0.0.0.0",
        val port: Int = 8082,
        val publicAddress: String = "http://$ip:$port"
    ) {
        fun publicUrl(hash: String, ids: TemplateIds) = "$publicAddress/$hash.zip?packs=${ids.joinToString(",")}"
    }

    @Serializable
    data class PackyMenu(
        val title: String = "Packy Pack Picker",
        val height: Int = 6,
        val subMenus: Map<String, PackySubMenu> = mapOf()
    )

    enum class SubMenuType {
        MENU, CYCLING
    }

    @Serializable
    data class PackySubMenu(
        val title: String = "Packy SubMenu",
        val height: Int = 6,
        @EncodeDefault(NEVER) val button: SerializableItemStack = ItemStack(Material.STONE).toSerializable(),
        val modifiers: Modifiers = Modifiers(),
        val type: SubMenuType = SubMenuType.MENU,
        val packs: Map<String, PackyPack> = mapOf()
    )

    @Serializable
    data class PackyPack(
        @EncodeDefault(NEVER) val button: SerializableItemStack? = null,
        @EncodeDefault(NEVER) val modifiers: Modifiers = Modifiers()
    )

    @Serializable
    data class Modifiers(val offset: Offset = Offset(), val size: Size = Size()) {
        fun toModifier(): Modifier = Modifier.at(offset.x, offset.y).size(size.width, size.height)
    }

    @Serializable
    data class Offset(val x: Int = 0, val y: Int = 0) {
        fun toAtModifier(modifier: Modifier = Modifier): Modifier = modifier.at(x, y)
    }

    @Serializable
    data class Size(val width: Int = 1, val height: Int = 1) {
        fun toSizeModifier(modifier: Modifier = Modifier): Modifier = modifier.size(width, height)
    }
}
