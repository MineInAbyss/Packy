package com.mineinabyss.packy

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.idofront.nms.interceptClientbound
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.PackyGenerator.cachedPacks
import com.mineinabyss.packy.PackyGenerator.cachedPacksByteArray
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.components.PackyPack
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.TemplateIds
import io.papermc.paper.adventure.PaperAdventure
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ConfigurationTask
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask
import org.bukkit.entity.Player
import team.unnamed.creative.ResourcePack
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
            .prompt(packy.config.prompt?.miniMsg())
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

    private val configurationTasks = ServerConfigurationPacketListenerImpl::class.java.getDeclaredField("configurationTasks").apply { isAccessible = true }
    private val startNextTaskMethod = ServerConfigurationPacketListenerImpl::class.java.getDeclaredMethod("startNextTask").apply { isAccessible = true }
    fun registerConfigPacketHandler() {

        packy.plugin.interceptClientbound { packet: Packet<*>, connection: Connection ->
            if (packet !is ClientboundSelectKnownPacks) return@interceptClientbound packet
            val configListener = connection.packetListener as? ServerConfigurationPacketListenerImpl ?: return@interceptClientbound packet
            val taskQueue = configurationTasks.get(configListener) as? Queue<ConfigurationTask> ?: return@interceptClientbound packet
            val offlinePdc = connection.player?.bukkitEntity?.getOfflinePDC() ?: return@interceptClientbound packet
            val packyData = offlinePdc.decode<PackyData>() ?: PackyData()

            // Removes the JoinWorldTask from the Queue
            val headTask = taskQueue.poll()

            // Runs next tick, after the queue progresses and is empty
            packy.plugin.launch {
                val info = PackyGenerator.getOrCreateCachedPack(packyData.enabledPackIds).await().resourcePackInfo
                taskQueue.add(
                    ServerResourcePackConfigurationTask(
                        MinecraftServer.ServerResourcePackInfo(
                            info.id(), info.uri().toString(), info.hash(),
                            packy.config.force && !packyData.bypassForced,
                            PaperAdventure.asVanilla(packy.config.prompt?.miniMsg())
                        ))
                )

                headTask?.let(taskQueue::add)
                startNextTaskMethod.invoke(configListener)
            }

            // Returns the ClientboundSelectKnownPacks packet, which causes the queue to continue
            // Since it is now empty it does nothing, until our coroutine finishes and triggers startNextTask
            return@interceptClientbound packet
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
        val data = exchange.requestURI.parseTemplateIds()?.let(cachedPacksByteArray::get)
            ?: ResourcePacks.resourcePackWriter.build(packy.defaultPack).data().toByteArray()
        exchange.responseHeaders["Content-Type"] = "application/zip"
        exchange.sendResponseHeaders(200, data.size.toLong())
        exchange.responseBody.use { responseStream -> responseStream.write(data) }
    }
}
