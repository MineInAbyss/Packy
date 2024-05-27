package com.mineinabyss.packy

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.datatypes.GearyEntity
import com.mineinabyss.geary.helpers.entity
import com.mineinabyss.geary.helpers.temporaryEntity
import com.mineinabyss.geary.modules.geary
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.geary.papermc.datastore.remove
import com.mineinabyss.geary.papermc.tracking.entities.gearyMobs
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.idofront.config.ConfigFormats
import com.mineinabyss.idofront.config.Format
import com.mineinabyss.idofront.config.config
import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.entities.toOfflinePlayer
import com.mineinabyss.idofront.messaging.broadcastVal
import com.mineinabyss.idofront.messaging.logVal
import com.mineinabyss.idofront.messaging.observeLogger
import com.mineinabyss.idofront.nms.nbt.WrappedPDC
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.config.*
import com.mineinabyss.packy.helpers.PackyDownloader
import com.mineinabyss.packy.helpers.PackyGenerator
import com.mineinabyss.packy.helpers.PackyServer
import com.mineinabyss.packy.listener.PlayerListener
import com.mineinabyss.packy.listener.TemplateLoadTriggers
import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.papermc.paper.adventure.PaperAdventure
import io.papermc.paper.network.ChannelInitializeListenerHolder
import io.th0rgal.oraxen.OraxenPlugin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.serialization.modules.EmptySerializersModule
import net.kyori.adventure.resource.ResourcePackInfo
import net.minecraft.network.Connection
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
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

        registerConfigurationPacketListener()
    }

    private fun registerConfigurationPacketListener() {
        val key = NamespacedKey.fromString("configuration_listener", this)
        ChannelInitializeListenerHolder.addListener(key!!) { channel: Channel ->
            channel.pipeline().addBefore("packet_handler", key.toString(), object : ChannelDuplexHandler() {
                private val connection = channel.pipeline()["packet_handler"] as Connection
                private val cachedPackyData = mutableMapOf<UUID, PackyData>()

                override fun write(ctx: ChannelHandlerContext, packet: Any, promise: ChannelPromise) {
                    if (packet is ClientboundFinishConfigurationPacket) {
                        connection.player.bukkitEntity.getOfflinePDC()?.decode<PackyData>()?.apply {
                            cachedPackyData[connection.player.uuid] = this
                            launch {
                                val info = PackyGenerator.getOrCreateCachedPack(enabledPackIds).await().resourcePackInfo
                                connection.send(
                                    ClientboundResourcePackPushPacket(
                                        info.id(), info.uri().toString(), info.hash(),
                                        packy.config.force && !bypassForced,
                                        Optional.of(PaperAdventure.asVanilla(packy.config.prompt.miniMsg()))
                                    )
                                )
                            }
                        } ?: ctx.write(packet, promise)
                    } else ctx.write(packet, promise)
                }

                override fun channelRead(ctx: ChannelHandlerContext, packet: Any) {
                    if (packet is ServerboundResourcePackPacket && finishConfigPhase(packet.action)) {
                        cachedPackyData[connection.player.uuid]?.let { packyData ->
                            PackyGenerator.getCachedPack(packyData.enabledPackIds)?.resourcePackInfo?.id()
                                ?.takeIf { it == packet.id }?.let {
                                    // We no longer need to listen or process ClientboundFinishConfigurationPacket that we send ourselves
                                    ctx.pipeline().remove(this)
                                    connection.send(ClientboundFinishConfigurationPacket.INSTANCE)
                                    cachedPackyData.remove(connection.player.uuid)
                                    return
                                }
                        }
                    }
                    ctx.fireChannelRead(packet)
                }
            })
        }
    }

    private fun finishConfigPhase(action: ServerboundResourcePackPacket.Action): Boolean {
        return when (action) {
            ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED -> true
            //TODO Figure out why SUCCESSFULLY_LOADED isnt sent and thus never completes the channelRead
            //ServerboundResourcePackPacket.Action.DOWNLOADED -> true
            ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD -> true
            ServerboundResourcePackPacket.Action.FAILED_RELOAD -> true
            ServerboundResourcePackPacket.Action.DISCARDED -> true
            else -> false
        }
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
