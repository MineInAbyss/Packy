package com.mineinabyss.packy.listener

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.geary.papermc.gearyPaper
import com.mineinabyss.packy.PackyGenerator
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent
import io.papermc.paper.event.connection.configuration.PlayerConnectionReconfigureEvent
import net.kyori.adventure.resource.ResourcePackRequest
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class PlayerListener : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun PlayerJoinEvent.filterPackyData() {
        val packyData = player.packyData
        // Remove old or required keys from templates
        packyData.templates.keys.filter { packy.templates[it] == null }.forEach(packyData.templates::remove)
        // Add missing template keys
        packy.templates.forEach { template -> packyData.templates.computeIfAbsent(template.id) { template.default || template.required } }
    }

    @EventHandler
    fun AsyncPlayerConnectionConfigureEvent.onConfig() {
        val pdc = Bukkit.getOfflinePlayer(connection.profile.id!!).persistentDataContainer
        val packyData = with(gearyPaper.worldManager.global) {
            pdc.decode<PackyData>() ?: PackyData()
        }
        val future = CompletableFuture<Void>()

        packy.plugin.launch(packy.plugin.minecraftDispatcher) {
            val info = PackyGenerator.getOrCreateCachedPack(packyData.enabledPackIds).await().resourcePackInfo
            connection.audience.sendResourcePacks(ResourcePackRequest.addingRequest(info).replace(true).callback { _, status, _ ->
                if (!status.intermediate()) future.complete(null)
            })
        }
        future.orTimeout(20, TimeUnit.SECONDS).join()
    }

    @EventHandler
    fun PlayerConnectionReconfigureEvent.onConfig() {
        val pdc = Bukkit.getOfflinePlayer(connection.profile.id!!).persistentDataContainer
        val packyData = with(gearyPaper.worldManager.global) {
            pdc.decode<PackyData>() ?: PackyData()
        }

        packy.plugin.launch(packy.plugin.minecraftDispatcher) {
            val info = PackyGenerator.getOrCreateCachedPack(packyData.enabledPackIds).await().resourcePackInfo
            connection.audience.sendResourcePacks(ResourcePackRequest.addingRequest(info).replace(true).callback { _, status, _ ->
                if (!status.intermediate()) connection.completeReconfiguration()
            })
        }
    }
}
