package com.mineinabyss.packy

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.idofront.events.call
import com.mineinabyss.idofront.nms.interceptClientbound
import com.mineinabyss.idofront.nms.interceptServerbound
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.TemplateIds
import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.papermc.paper.adventure.PaperAdventure
import io.papermc.paper.network.ChannelInitializeListenerHolder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.network.Connection
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerResourcePackStatusEvent
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.server.ResourcePackServer
import team.unnamed.creative.server.handler.ResourcePackRequestHandler
import java.net.URI
import java.util.*
import java.util.concurrent.Executors


object PackyServer {
    var packServer: ResourcePackServer? = null

    suspend fun sendPack(player: Player) {
        val templateIds = player.packyData.enabledPackIds
        val actionBarJob = packy.plugin.launch { while (isActive) player.sendPackGeneratingActionBar() }
        val resourcePack = PackyGenerator.getOrCreateCachedPack(templateIds).apply { invokeOnCompletion { actionBarJob.cancel() } }.await()

        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
            .packs(resourcePack.resourcePackInfo).replace(true)
            .required(packy.config.force && !player.packyData.bypassForced)
            .prompt(packy.config.prompt.miniMsg())
        )
    }

    private suspend fun Player.sendPackGeneratingActionBar() {
        sendActionBar(Component.text("Generating ResourcePack.", NamedTextColor.RED))
        delay(10.ticks)
        sendActionBar(Component.text("Generating ResourcePack..", NamedTextColor.RED))
        delay(10.ticks)
        sendActionBar(Component.text("Generating ResourcePack...", NamedTextColor.RED))
        delay(10.ticks)
    }

    private val cachedPackyData = mutableMapOf<UUID, PackyData>()
    fun registerConfigPacketHandler() {
        packy.plugin.interceptClientbound { packet, player ->
            if (player == null) return@interceptClientbound packet
            if (packet !is ClientboundFinishConfigurationPacket) return@interceptClientbound packet
            if (player.resourcePackStatus != null) return@interceptClientbound packet
            val packyData = player.getOfflinePDC()?.decode<PackyData>() ?: return@interceptClientbound packet

            cachedPackyData[player.uniqueId] = packyData
            packy.plugin.launch {
                val info = PackyGenerator.getOrCreateCachedPack(packyData.enabledPackIds).await().resourcePackInfo
                (player as CraftPlayer).handle.connection.send(
                    ClientboundResourcePackPushPacket(
                        info.id(), info.uri().toString(), info.hash(),
                        packy.config.force && !packyData.bypassForced,
                        Optional.of(PaperAdventure.asVanilla(packy.config.prompt.miniMsg()))
                    )
                )
            }

            return@interceptClientbound null
        }

        packy.plugin.interceptServerbound { packet, player ->
            if (player == null) return@interceptServerbound packet
            if (packet !is ServerboundResourcePackPacket || !packet.isTerminal) return@interceptServerbound packet
            val packyData = cachedPackyData[player.uniqueId] ?: return@interceptServerbound packet
            PackyGenerator.getCachedPack(packyData.enabledPackIds)?.resourcePackInfo?.id()?.takeIf { it == packet.id } ?: return@interceptServerbound packet

            PlayerResourcePackStatusEvent(player, packet.id, PlayerResourcePackStatusEvent.Status.valueOf(packet.action.name)).call()
            (player as CraftPlayer).handle.connection.send(ClientboundFinishConfigurationPacket.INSTANCE)
            cachedPackyData.remove(player.uniqueId)
            null
        }
    }

    fun startServer() {
        packy.logger.s("Starting Packy-Server...")
        val (ip, port) = packy.config.server.let { it.ip to it.port }
        packServer = ResourcePackServer.server().address(ip, port).handler(handler).executor(Executors.newFixedThreadPool(20)).build()
        packServer?.start()
    }

    fun stopServer() {
        packy.logger.i("Stopping Packy-Server...")
        packServer?.stop(0)
    }

    private fun URI.parseTemplateIds(): TemplateIds? {
        // split query string into map
        val queryMap = query.split('&').mapNotNull {
            it.split('=', limit = 2).takeIf { it.size == 2 }?.let { it.first() to it.last() }
        }.toMap()
        return queryMap["packs"]?.split(",")?.toSortedSet()
    }

    private val handler = ResourcePackRequestHandler { _, exchange ->
        val data = exchange.requestURI.parseTemplateIds()
            ?.let { templateIds -> PackyGenerator.cachedPacksByteArray[templateIds] }
            ?: MinecraftResourcePackWriter.minecraft().build(packy.defaultPack).data().toByteArray()
        exchange.responseHeaders["Content-Type"] = "application/zip"
        exchange.sendResponseHeaders(200, data.size.toLong())
        exchange.responseBody.use { responseStream -> responseStream.write(data) }
    }
}
