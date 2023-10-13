package com.mineinabyss.packy

import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.toPath
import team.unnamed.creative.BuiltResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import kotlin.io.path.div

object PackyGenerator {

    fun setupInitialFiles() {
        packy.pack.icon(Writable.path(packy.plugin.dataFolder.toPath() / packy.config.icon))
        packy.pack.packMeta(packy.config.mcmeta.format, packy.config.mcmeta.description)
    }

    fun buildPack(): BuiltResourcePack {
        zipPack()
        return MinecraftResourcePackWriter.minecraft().build(packy.pack)
    }

    fun zipPack() {
        logSuccess("Zipping up pack")
        MinecraftResourcePackWriter.minecraft().writeToDirectory(packy.config.zipDestination.toPath().toFile(), packy.pack)
    }
}
