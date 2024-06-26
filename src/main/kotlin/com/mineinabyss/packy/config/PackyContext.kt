package com.mineinabyss.packy.config

import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.messaging.ComponentLogger
import com.mineinabyss.packy.PackyPlugin
import team.unnamed.creative.ResourcePack

val packy by DI.observe<PackyContext>()
interface PackyContext {
    val plugin: PackyPlugin
    val config: PackyConfig
    val defaultPack: ResourcePack
    val templates: Map<String, PackyTemplate>
    val accessToken: PackyAccessToken
    val logger: ComponentLogger
}
