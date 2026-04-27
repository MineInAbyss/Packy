package com.mineinabyss.packy

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.dependencies.addCloseable
import com.mineinabyss.dependencies.module
import com.mineinabyss.dependencies.new
import com.mineinabyss.idofront.features.listeners
import com.mineinabyss.idofront.features.mainCommand
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.listener.PlayerListener
import com.mineinabyss.packy.listener.TemplateLoadTriggers

val PackyMainFeature = module("packy") {
    PackyServer.startServer()
    PackyDownloader.downloadTemplates()
    TemplateLoadTriggers.registerTemplateHandlers()
    PackyGenerator.setupRequiredPackTemplates()
    listeners(new(::PlayerListener))
    addCloseable {
        TemplateLoadTriggers.unregisterTemplateHandlers()
        PackyServer.stopServer()
        PackyGenerator.activeGeneratorJob.onEach { it.value.cancel() }.clear()
        PackyGenerator.cachedPacks.clear()
        PackyGenerator.cachedPacksByteArray.clear()
    }
    packy.launch {
        if (packy.config.sendOnReload) packy.server.onlinePlayers.forEach {
            if (packy.config.reconfigureOnReload) it.connection.reenterConfiguration()
            else PackyServer.sendPack(it)
        }
    }
}.mainCommand {
    packySubcommands()
}