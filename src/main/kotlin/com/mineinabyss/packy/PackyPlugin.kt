package com.mineinabyss.packy

import com.charleskorn.kaml.YamlConfiguration
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.idofront.config.ConfigFormats
import com.mineinabyss.idofront.config.Format
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.platforms.Platforms
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.packy.config.*
import com.mineinabyss.packy.helpers.PackyDownloader
import com.mineinabyss.packy.helpers.PackyGenerator
import com.mineinabyss.packy.helpers.PackyGenerator.stopAll
import com.mineinabyss.packy.helpers.PackyServer
import com.mineinabyss.packy.listener.PlayerListener
import com.mineinabyss.packy.menus.picker.PackPicker
import kotlinx.serialization.StringFormat
import kotlinx.serialization.modules.EmptySerializersModule
import org.apache.maven.model.InputLocation.StringFormatter
import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.Yaml
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

        geary {
            autoscan(classLoader, "com.mineinabyss.packy") {
                all()
            }
        }
    }

    override fun onDisable() {
        //PackPicker.activePickerJob.stopAll()
        //PackyGenerator.activeGeneratorJob.stopAll()
        PackyServer.playerPacks.clear()
        PackyServer.stopServer()
    }

    fun createPackyContext() {
        DI.remove<PackyContext>()
        DI.add<PackyContext>(object : PackyContext {
            override val plugin = this@PackyPlugin
            override val config: PackyConfig by config("config", dataFolder.toPath(), PackyConfig())
            override val defaultPack: ResourcePack = ResourcePack.resourcePack()
            override val templates: Map<String, PackyTemplate> = config<PackyTemplates>("templates", dataFolder.toPath(), PackyTemplates()).getOrLoad().templates
            override val accessToken: PackyAccessToken by config("accessToken", dataFolder.toPath(), PackyAccessToken())
        })
    }
}
