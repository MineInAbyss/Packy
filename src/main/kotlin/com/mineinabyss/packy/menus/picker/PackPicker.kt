package com.mineinabyss.packy.menus.picker

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.messaging.warn
import com.mineinabyss.packy.helpers.PackyGenerator
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.components.removeConflictingPacks
import com.mineinabyss.packy.config.packy
import kotlinx.coroutines.Job
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

object PackPicker {
    val activePickerJob: MutableMap<UUID, Job?> = mutableMapOf()
    fun addPack(player: Player, pack: String, sender: CommandSender = player): Unit? {
        if (activePickerJob[player.uniqueId] != null) return null
        activePickerJob[player.uniqueId] = packy.plugin.launch(packy.plugin.asyncDispatcher) {
            packy.templates.entries.find { it.key == pack }?.let { (id, template) ->
                val removedConflicting = player.removeConflictingPacks(template).map { it.id }
                player.packyData.enabledPackAddons += template

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
        }.apply { invokeOnCompletion { activePickerJob -= player.uniqueId } }
        return Unit
    }

    fun removePack(player: Player, pack: String, sender: CommandSender = player): Unit? {
        if (activePickerJob[player.uniqueId] != null) return null
        activePickerJob[player.uniqueId] = packy.plugin.launch(packy.plugin.asyncDispatcher) {
            packy.templates.entries.find { it.key == pack }?.let { (id, template) ->
                player.packyData.enabledPackAddons -= template

                if ((sender as? Player)?.uniqueId != player.uniqueId) sender.success("TemplatePack $id was removed from ${player.name}'s addon-packs")
                player.success("TemplatePack $id was removed from your addon-packs")
            } ?: when {
                (sender as? Player)?.uniqueId != player.uniqueId ->
                    sender.error("The template could not be removed from ${player.name}'s addon-packs")

                else -> sender.error("The template could not be removed from your addon-packs")
            }
        }.apply { invokeOnCompletion { activePickerJob -= player.uniqueId } }
        return Unit
    }
}
