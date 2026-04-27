package com.mineinabyss.packy.config

import com.mineinabyss.idofront.messaging.ComponentLogger
import org.bukkit.plugin.Plugin
import team.unnamed.creative.ResourcePack

val packy get() = PackyContext.instance ?: error("PackyContext not initialized")

interface PackyContext : Plugin {
    val config: PackyConfig
    val menu: PackyMenu
    val defaultPack: ResourcePack
    val templates: PackyTemplates
    val accessToken: PackyAccessToken
    val logger: ComponentLogger

    companion object {
        var instance: PackyContext? = null
    }
}
