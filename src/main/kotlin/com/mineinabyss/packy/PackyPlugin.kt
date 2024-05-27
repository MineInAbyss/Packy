package com.mineinabyss.packy

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.idofront.config.ConfigFormats
import com.mineinabyss.idofront.config.Format
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.messaging.observeLogger
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.config.*
import com.mineinabyss.packy.listener.PlayerListener
import com.mineinabyss.packy.listener.TemplateLoadTriggers
import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.papermc.paper.adventure.PaperAdventure
import io.papermc.paper.network.ChannelInitializeListenerHolder
import kotlinx.coroutines.Job
import kotlinx.serialization.modules.EmptySerializersModule
import net.minecraft.network.Connection
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin
import team.unnamed.creative.ResourcePack
import java.util.*

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
        PackyCommands()
        PackyServer.startServer()

        listeners(PlayerListener())
    }

    override fun onDisable() {
        PackyServer.stopServer()
    }

    fun createPackyContext() {
        TemplateLoadTriggers.unregisterTemplateHandlers()

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

        PackyServer.registerConfigurationPacketListener()
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
