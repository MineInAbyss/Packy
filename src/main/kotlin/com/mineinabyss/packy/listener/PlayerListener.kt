package com.mineinabyss.packy.listener

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyServer
import kotlinx.coroutines.delay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import team.unnamed.creative.model.Model
import kotlin.time.Duration.Companion.seconds

class PlayerListener : Listener {
    @EventHandler
    fun PlayerJoinEvent.onJoin() {
        packy.plugin.launch {
            delay(1.seconds)
            PackyServer.sendToPlayer(player)
        }
    }
}
