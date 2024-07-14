package com.mineinabyss.packy.helpers

import net.kyori.adventure.key.Key
import org.bukkit.Material
import team.unnamed.creative.model.Model
import team.unnamed.creative.texture.Texture
import java.util.*

object VanillaKeys {
    fun isVanilla(model: Model): Boolean {
        return model.key() in defaultBlockKeys || model.key() in defaultItemKeys
    }

    fun isVanilla(texture: Texture): Boolean {
        return texture.key() in defaultBlockKeys || texture.key() in defaultItemKeys
    }

    fun isVanilla(key: Key): Boolean {
        return key in defaultBlockKeys || key in defaultItemKeys
    }

    private val defaultItemKeys: List<Key> = Material.entries.toTypedArray().filter { !it.isLegacy && it.isItem }
        .map { Key.key("minecraft", "item/" + it.key.value()) }
    private val defaultBlockKeys: List<Key> = Material.entries.toTypedArray().filter { !it.isLegacy && it.isBlock }
            .map { Key.key("minecraft", "block/" + it.key.value()) }
}
