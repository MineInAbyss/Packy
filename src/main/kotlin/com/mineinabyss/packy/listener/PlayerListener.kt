package com.mineinabyss.packy.listener

import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerListener : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun PlayerJoinEvent.filterPackyData() {
        // Remove old or forced keys from enabledPackAddons
        player.packyData.enabledPackAddons.removeIf { t -> t.id !in packy.templates.keys || t.required }
        // Ensure that PackyTemplates are up-to-date
        player.packyData.enabledPackAddons.filter { it in packy.templates.values }.forEach { template ->
            player.packyData.enabledPackAddons -= template
            packy.templates.entries.find { it.key == template.id }?.value?.let {
                player.packyData.enabledPackAddons += it
            }
        }
    }
}
