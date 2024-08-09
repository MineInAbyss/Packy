package com.mineinabyss.packy.listener

import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerListener : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun PlayerJoinEvent.filterPackyData() {
        val packyData = player.packyData
        // Remove old or required keys from templates
        packyData.templates.keys.filter { packy.templates[it] == null }.forEach(packyData.templates::remove)
        // Add missing template keys
        packy.templates.forEach { template -> packyData.templates.computeIfAbsent(template.id) { template.default || template.required } }
    }
}
