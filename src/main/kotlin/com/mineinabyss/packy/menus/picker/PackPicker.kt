package com.mineinabyss.packy.menus.picker

import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.messaging.warn
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.components.removeConflictingPacks
import com.mineinabyss.packy.config.packy
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object PackPicker {
    fun addPack(scope: PackyUIScope, player: Player, packyData: PackyData, pack: String, sender: CommandSender = player): Unit? {
        if (pack !in packy.templates.keys) return null
        return packy.templates.entries.find { it.key == pack }?.let { (id, template) ->
            val removedConflicting = player.removeConflictingPacks(template).map { it.id }
            packyData.enabledPackAddons += template

            if ((sender as? Player)?.uniqueId != player.uniqueId) sender.success("TemplatePack $id was added to ${player.name}'s addon-packs")
            scope.changedAction = {
                player.success("The template $id was added to your addon-packs")
                if (removedConflicting.isNotEmpty()) {
                    sender.warn("Removed conflicting pack-templates: ${removedConflicting.joinToString(", ")}")
                }
            }
        } ?: run {
            scope.changedAction = {
                when {
                    (sender as? Player)?.uniqueId != player.uniqueId ->
                        sender.error("The template could not be removed from ${player.name}'s addon-packs")
                    else -> sender.error("The template could not be removed from your addon-packs")
                }
            }
        }
    }

    fun removePack(scope: PackyUIScope, player: Player, packyData: PackyData, pack: String, sender: CommandSender = player): Unit? {
        if (pack !in packy.templates.keys) return null
        return packy.templates.entries.find { it.key == pack }?.let { (id, template) ->
            packyData.enabledPackAddons -= template

            scope.changedAction = {
                if ((sender as? Player)?.uniqueId != player.uniqueId) sender.success("TemplatePack $id was removed from ${player.name}'s addon-packs")
                player.success("TemplatePack $id was removed from your addon-packs")
            }
        } ?: run {
            scope.changedAction = {
                when {
                    (sender as? Player)?.uniqueId != player.uniqueId ->
                        sender.error("The template could not be removed from ${player.name}'s addon-packs")
                    else -> sender.error("The template could not be removed from your addon-packs")
                }
            }
        }
    }
}
