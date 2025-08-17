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
    @EncodeDefault(ALWAYS) val reconfigureOnReload: Boolean = false,
    @YamlComment("What ObfuscationType to use, valid options are FULL, SIMPLE & NONE")
    @EncodeDefault(ALWAYS) val obfuscation: ObfuscationType = ObfuscationType.SIMPLE,
    @YamlComment("This will use PackSquash to automatically squash all templates")
    @EncodeDefault(ALWAYS) val packSquash: PackSquash = PackSquash(),
    @YamlComment(
        "The amount of TemplateID combinations Packy should cache the ResourcePack off",
        "If your ResourcePack is large it is recommended to lower this value"
    )
    @EncodeDefault(ALWAYS) val cachedPackAmount: Int = 10
) {

    enum class ObfuscationType {
        FULL, SIMPLE, NONE
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
    data class PackyMcMeta(val format: Int = 64, val description: String = "Packy Resourcepack")

    @Serializable
    data class PackyServer(
        @YamlComment("Change this to your server's actual IP, unless on localhost")
        val ip: String = "0.0.0.0",
        val port: Int = 8082,
        val publicAddress: String = "http://$ip:$port"
    ) {
        fun publicUrl(hash: String, ids: TemplateIds) = "$publicAddress/$hash.zip?packs=${ids.joinToString(",")}"
    }
}
