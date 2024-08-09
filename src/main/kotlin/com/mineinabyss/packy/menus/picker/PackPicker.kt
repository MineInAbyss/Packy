package com.mineinabyss.packy.menus.picker

import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.messaging.warn
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object PackPicker {
    fun enablePack(scope: PackyUIScope, player: Player, packyData: PackyData, pack: String, sender: CommandSender = player): Unit? {
        return packy.templates[pack]?.let { template ->
            val disabledConflicting = disableConflictingPacks(player, template).map { it.id }
            packyData.templates[template.id] = true

            if ((sender as? Player)?.uniqueId != player.uniqueId) sender.success("TemplatePack ${template.id} was added to ${player.name}'s addon-packs")
            scope.changedAction = {
                player.success("The template ${template.id} was added to your addon-packs")
                if (disabledConflicting.isNotEmpty()) {
                    sender.warn("Disabled conflicting pack-templates: ${disabledConflicting.joinToString(", ")}")
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

    fun disablePack(scope: PackyUIScope, player: Player, packyData: PackyData, pack: String, sender: CommandSender = player): Unit? {
        return packy.templates.find { it.id == pack }?.let { template ->
            packyData.templates[template.id] = false

            scope.changedAction = {
                if ((sender as? Player)?.uniqueId != player.uniqueId) sender.success("TemplatePack ${template.id} was removed from ${player.name}'s addon-packs")
                player.success("TemplatePack ${template.id} was disabled from your addon-packs")
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

    private fun disableConflictingPacks(player: Player, template: PackyTemplate): Set<PackyTemplate> {
        return player.packyData.templates.keys.mapNotNull(packy.templates::get).filter(template::conflictsWith).toSet().also {
            player.packyData.templates.putAll(it.associate { it.id to false })
        }
    }
}
