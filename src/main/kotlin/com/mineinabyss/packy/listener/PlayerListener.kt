package com.mineinabyss.packy.listener

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.packy.PackyGenerator
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyServer
import com.mineinabyss.packy.helpers.PackyServer.playerPack
import kotlinx.coroutines.delay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import kotlin.time.Duration.Companion.seconds

class PlayerListener : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun PlayerJoinEvent.sendPack() {
        if (PackyServer.packUploaded) packy.plugin.launch {
            delay(1.seconds)
            PackyServer.sendPack(player)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun PlayerJoinEvent.onJoin() {
        player.playerPack = PackyGenerator.createPlayerPack(player)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun PlayerJoinEvent.filterPackyData() {
        player.packyData.enabledPackAddons.removeIf { t -> t.id !in packy.templates.map { it.id } }
    }
}
