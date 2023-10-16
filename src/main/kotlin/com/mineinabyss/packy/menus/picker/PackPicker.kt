package com.mineinabyss.packy.menus.picker

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.logWarn
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.messaging.warn
import com.mineinabyss.packy.PackyGenerator
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.components.removeConflictingPacks
import com.mineinabyss.packy.config.packy
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object PackPicker {
    fun addPack(player: Player, pack: String, sender: CommandSender? = null) {
        packy.plugin.launch(packy.plugin.asyncDispatcher) {
            packy.templates.find { it.id == pack }?.let { template ->
                val removedConflicting = player.removeConflictingPacks(template).map { it.id }
                player.packyData.enabledPackAddons.add(template)

                if ((sender as? Player)?.uniqueId != player.uniqueId) sender?.success("TemplatePack ${template.id} was added to ${player.name}'s addon-packs")
                player.success("The template ${template.id} was added to your addon-packs")
                if (removedConflicting.isNotEmpty()) {
                    sender?.warn("Removed conflicting pack-templates: ${removedConflicting.joinToString(", ")}")
                }
                PackyGenerator.createPlayerPack(player)
            } ?: when {
                (sender as? Player)?.uniqueId != player.uniqueId ->
                    sender?.error("The template could not be removed from ${player.name}'s addon-packs")
                else -> sender.error("The template could not be removed from your addon-packs")
            }
        }
    }

    fun removePack(player: Player, pack: String, sender: CommandSender? = null) {
        packy.plugin.launch(packy.plugin.asyncDispatcher) {
            packy.templates.find { it.id == pack }?.let { template ->
                player.packyData.enabledPackAddons.remove(template)

                if ((sender as? Player)?.uniqueId != player.uniqueId) sender?.success("TemplatePack ${template.id} was removed from ${player.name}'s addon-packs")
                player.success("TemplatePack ${template.id} was removed from your addon-packs")
                PackyGenerator.createPlayerPack(player)
            } ?: when {
                (sender as? Player)?.uniqueId != player.uniqueId ->
                    sender?.error("The template could not be removed from ${player.name}'s addon-packs")

                else -> sender.error("The template could not be removed from your addon-packs")
            }
        }
    }
}
