package com.mineinabyss.packy.helpers

import net.kyori.adventure.key.Key
import org.bukkit.Material
import team.unnamed.creative.model.Model
import team.unnamed.creative.texture.Texture
import java.util.*

object VanillaKeys {
    fun isVanilla(model: Model): Boolean {
        return defaultBlockKeys.contains(model.key()) || defaultItemKeys.contains(model.key())
    }

    fun isVanilla(texture: Texture): Boolean {
        return defaultBlockKeys.contains(texture.key()) || defaultItemKeys.contains(texture.key())
    }

    fun isVanilla(key: Key): Boolean {
        return defaultBlockKeys.contains(key) || defaultItemKeys.contains(key)
    }

    private val defaultItemKeys: List<Key> =
        Arrays.stream(Material.entries.toTypedArray()).filter { m: Material -> !m.isLegacy && m.isItem }
            .map { m: Material ->
                Key.key(
                    "minecraft",
                    "item/" + m.key.value()
                )
            }.toList()
    private val defaultBlockKeys: List<Key> =
        Arrays.stream(Material.entries.toTypedArray()).filter { m: Material -> !m.isLegacy && m.isBlock }
            .map { m: Material ->
                Key.key(
                    "minecraft",
                    "block/" + m.key.value()
                )
            }.toList()
}
