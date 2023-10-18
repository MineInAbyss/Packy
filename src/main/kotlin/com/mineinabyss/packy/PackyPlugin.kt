package com.mineinabyss.packy

import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.platforms.Platforms
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.PackyContext
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.helpers.PackyDownloader
import com.mineinabyss.packy.helpers.PackyServer
import com.mineinabyss.packy.listener.PlayerListener
import org.bukkit.plugin.java.JavaPlugin
import team.unnamed.creative.ResourcePack

class PackyPlugin : JavaPlugin() {

    override fun onLoad() {
        Platforms.load(this, "mineinabyss")
    }

    override fun onEnable() {
        createPackyContext()
        PackyCommands()
        PackyDownloader.downloadTemplates()
        PackyGenerator.setupForcedPackFiles()
        PackyServer.startServer()

        listeners(PlayerListener())

//        geary {
//            autoscan(classLoader, "com.mineinabyss.packy") {
//                all()
//            }
//        }
    }

    override fun onDisable() {
        PackyServer.stopServer()
    }

    fun createPackyContext() {
        DI.remove<PackyContext>()
        DI.add<PackyContext>(object : PackyContext {
            override val plugin = this@PackyPlugin
            override val config: PackyConfig by config("config", dataFolder.toPath(), PackyConfig())
            override val defaultPack: ResourcePack = ResourcePack.resourcePack()
            override val templates: Set<PackyTemplate> = config<Set<PackyTemplate>>("templates", dataFolder.toPath(), setOf()).getOrLoad()
        })
    }
}
