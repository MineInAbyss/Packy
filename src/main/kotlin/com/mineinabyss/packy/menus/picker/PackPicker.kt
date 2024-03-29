package com.mineinabyss.packy.menus.picker

import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.messaging.warn
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.components.removeConflictingPacks
import com.mineinabyss.packy.config.packy
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object PackPicker {
    fun addPack(player: Player, pack: String, packyData: PackyData, sender: CommandSender = player): Unit? {
        if (pack !in packy.templates.keys) return null
        return packy.templates.entries.find { it.key == pack }?.let { (id, template) ->
            val removedConflicting = player.removeConflictingPacks(template).map { it.id }
            packyData.enabledPackAddons += template

            if ((sender as? Player)?.uniqueId != player.uniqueId) sender.success("TemplatePack $id was added to ${player.name}'s addon-packs")
            player.success("The template $id was added to your addon-packs")
            if (removedConflicting.isNotEmpty()) {
                sender.warn("Removed conflicting pack-templates: ${removedConflicting.joinToString(", ")}")
            }
        } ?: when {
            (sender as? Player)?.uniqueId != player.uniqueId ->
                sender.error("The template could not be removed from ${player.name}'s addon-packs")
            else -> sender.error("The template could not be removed from your addon-packs")
        }
    }

    fun removePack(player: Player, pack: String, packyData: PackyData, sender: CommandSender = player): Unit? {
        if (pack !in packy.templates.keys) return null
        return packy.templates.entries.find { it.key == pack }?.let { (id, template) ->
            packyData.enabledPackAddons -= template

            if ((sender as? Player)?.uniqueId != player.uniqueId) sender.success("TemplatePack $id was removed from ${player.name}'s addon-packs")
            player.success("TemplatePack $id was removed from your addon-packs")
        } ?: when {
            (sender as? Player)?.uniqueId != player.uniqueId ->
                sender.error("The template could not be removed from ${player.name}'s addon-packs")

            else -> sender.error("The template could not be removed from your addon-packs")
        }
    }
}
