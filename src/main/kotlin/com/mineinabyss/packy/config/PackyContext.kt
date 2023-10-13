package com.mineinabyss.packy.config

import com.mineinabyss.idofront.di.DI
import com.mineinabyss.packy.PackyPlugin
import team.unnamed.creative.ResourcePack

val packy by DI.observe<PackyContext>()
interface PackyContext {
    val plugin: PackyPlugin
    val config: PackyConfig
    val pack: ResourcePack
}
