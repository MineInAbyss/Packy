package com.mineinabyss.packy.listener

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyGenerator
import com.mineinabyss.packy.helpers.PackyServer
import kotlinx.coroutines.delay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import kotlin.time.Duration.Companion.seconds

class PlayerListener : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun PlayerJoinEvent.sendPack() {
        if (PackyServer.packServer != null) packy.plugin.launch {
            delay(packy.config.packSendDelay)
            PackyServer.sendPack(player, PackyGenerator.getOrCreateCachedPack(player).await())
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun PlayerJoinEvent.filterPackyData() {
        // Remove old or forced keys from enabledPackAddons
        player.packyData.enabledPackAddons.removeIf { t -> t.id !in packy.templates.keys || t.forced }
        // Ensure that PackyTemplates are up-to-date
        player.packyData.enabledPackAddons.forEach {  template ->
            if (template in packy.templates.values) return@forEach
            player.packyData.enabledPackAddons -= template
            packy.templates.entries.find { it.key == template.id }?.value?.let {
                player.packyData.enabledPackAddons += it
            }
        }
    }
}
