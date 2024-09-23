package com.mineinabyss.packy.listener

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.PackyGenerator
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import io.papermc.paper.adventure.PaperAdventure
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.resource.ResourcePackRequest
import net.minecraft.server.MinecraftServer.ServerResourcePackInfo
import net.minecraft.server.network.ConfigurationTask
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLinksSendEvent
import java.util.*

class PlayerListener : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun PlayerJoinEvent.filterPackyData() {
        val packyData = player.packyData
        // Remove old or required keys from templates
        packyData.templates.keys.filter { packy.templates[it] == null }.forEach(packyData.templates::remove)
        // Add missing template keys
        packy.templates.forEach { template -> packyData.templates.computeIfAbsent(template.id) { template.default || template.required } }
    }

    private val configurationTasks = ServerConfigurationPacketListenerImpl::class.java.getDeclaredField("configurationTasks").apply { isAccessible = true }
    @EventHandler
    fun PlayerLinksSendEvent.sendResourcePackPreJoin() {
        if (player.isOnline) return

        val packetListener = (player as CraftPlayer).handle.server.connection.connections
            .map { it.packetListener }.filterIsInstance<ServerConfigurationPacketListenerImpl>()
            .firstOrNull { it.craftPlayer.uniqueId == player.uniqueId } ?: return
        val taskQueue = configurationTasks.get(packetListener) as? Queue<ConfigurationTask> ?: return
        val packyData = player.getOfflinePDC()?.decode<PackyData>() ?: PackyData()

        val info = PackyGenerator.getCachedPack(packyData.enabledPackIds)?.resourcePackInfo
        if (info != null) {
            if (player.isOnline) player.sendResourcePacks(ResourcePackRequest.addingRequest(info))
            else {
                taskQueue.offer(
                    ServerResourcePackConfigurationTask(
                        ServerResourcePackInfo(info.id(), info.uri().toString(), info.hash(), packy.config.force && !packyData.bypassForced, PaperAdventure.asVanilla(
                            packy.config.prompt?.miniMsg()))
                    )
                )
            }
        } else {
            packy.plugin.launch {
                val info = PackyGenerator.getOrCreateCachedPack(packyData.enabledPackIds).await().resourcePackInfo
                if (player.isOnline) player.sendResourcePacks(ResourcePackRequest.addingRequest(info))
            }
        }
    }
}
