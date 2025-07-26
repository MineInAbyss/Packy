package com.mineinabyss.packy.listener

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.mineinabyss.geary.helpers.temporaryEntity
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.geary.papermc.datastore.loadComponentsFrom
import com.mineinabyss.geary.papermc.gearyPaper
import com.mineinabyss.geary.papermc.withGeary
import com.mineinabyss.idofront.nms.nbt.getOfflinePDC
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.PackyGenerator
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import io.papermc.paper.adventure.PaperAdventure
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent
import io.papermc.paper.event.connection.configuration.PlayerConnectionReconfigureEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.job
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import net.kyori.adventure.resource.ResourcePackRequest
import net.minecraft.server.MinecraftServer.ServerResourcePackInfo
import net.minecraft.server.network.ConfigurationTask
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask
import net.minecraft.util.ProblemReporter
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLinksSendEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

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
            connection.audience.sendResourcePacks(ResourcePackRequest.addingRequest(info).callback { _, status, _ ->
                if (!status.intermediate()) future.complete(null)
            })
        }
        future.orTimeout(10, TimeUnit.SECONDS).join()
    }

    @EventHandler
    fun PlayerConnectionReconfigureEvent.onConfig() {
        val pdc = Bukkit.getOfflinePlayer(connection.profile.id!!).persistentDataContainer
        val packyData = with(gearyPaper.worldManager.global) {
            pdc.decode<PackyData>() ?: PackyData()
        }

        packy.plugin.launch(packy.plugin.minecraftDispatcher) {
            val info = PackyGenerator.getOrCreateCachedPack(packyData.enabledPackIds).await().resourcePackInfo
            connection.audience.sendResourcePacks(ResourcePackRequest.addingRequest(info).callback { _, status, _ ->
                if (!status.intermediate()) connection.completeReconfiguration()
            })
        }
    }
}
