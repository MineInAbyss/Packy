package com.mineinabyss.packy

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.idofront.config.ConfigFormats
import com.mineinabyss.idofront.config.Format
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.messaging.observeLogger
import com.mineinabyss.idofront.nms.PacketListener
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.packy.config.*
import com.mineinabyss.packy.listener.PlayerListener
import com.mineinabyss.packy.listener.TemplateLoadTriggers
import kotlinx.coroutines.Job
import kotlinx.serialization.modules.EmptySerializersModule
import org.bukkit.plugin.java.JavaPlugin
import team.unnamed.creative.ResourcePack

class PackyPlugin : JavaPlugin() {

    override fun onLoad() {
        geary {
            autoscan(classLoader, "com.mineinabyss.packy") {
                all()
            }
        }
    }

    override fun onEnable() {
        createPackyContext()
        PackyCommands.registerCommands()
        PackyServer.startServer()

        listeners(PlayerListener())
    }

    override fun onDisable() {
        PackyServer.stopServer()
    }

    fun createPackyContext() {
        TemplateLoadTriggers.unregisterTemplateHandlers()
        PacketListener.unregisterListener(this)

        DI.remove<PackyContext>()
        DI.add<PackyContext>(object : PackyContext {
            override val plugin = this@PackyPlugin
            override val config: PackyConfig by config("config", dataFolder.toPath(), PackyConfig())
            override val templates: Map<String, PackyTemplate> = config<PackyTemplates>(
                "templates", dataFolder.toPath(), PackyTemplates(), formats = templateFormat
            ).getOrLoad().templateMap
            override val accessToken: PackyAccessToken by config("accessToken", dataFolder.toPath(), PackyAccessToken())
            override val defaultPack: ResourcePack = ResourcePack.resourcePack()
            override val logger by plugin.observeLogger()
        })

        PackyGenerator.activeGeneratorJob.apply { values.forEach(Job::cancel) }.clear()
        PackyGenerator.cachedPacks.clear()
        PackyGenerator.cachedPacksByteArray.clear()
        PackyDownloader.downloadTemplates()
        TemplateLoadTriggers.registerTemplateHandlers()
        PackyGenerator.setupForcedPackFiles()
        PackyServer.registerConfigPacketHandler()
    }



    private val templateFormat = ConfigFormats(
        listOf(
            Format(
                "yml", Yaml(
                    serializersModule = EmptySerializersModule(),
                    YamlConfiguration(
                        polymorphismStyle = PolymorphismStyle.Property,
                        encodeDefaults = true,
                        strictMode = false,
                        sequenceBlockIndent = 2,
                        singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous
                    )
                )
            )
        )
    )
}
