package com.mineinabyss.packy.config

import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.messaging.ComponentLogger
import com.mineinabyss.packy.PackyPlugin
import team.unnamed.creative.BuiltResourcePack
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReader
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter

val packy by DI.observe<PackyContext>()
interface PackyContext {
    val plugin: PackyPlugin
    val config: PackyConfig
    val menu: PackyMenu
    val defaultPack: ResourcePack
    val templates: PackyTemplates
    val accessToken: PackyAccessToken
    val logger: ComponentLogger
}
