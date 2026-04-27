package com.mineinabyss.packy

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.guiy.canvas.guiy
import com.mineinabyss.idofront.commands.brigadier.IdoCommand
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.menus.picker.PackyMenu
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

fun IdoCommand.packySubcommands() {
    "menu" {
        permission = "packy.default"
        executes.asPlayer {
            guiy(player) { PackyMenu() }
        }
    }
    "send" {
        permission = "packy.default"
        executes.asPlayer {
            packy.launch {
                PackyServer.sendPack(player)
                sender.success("Sent pack to ${player.name}")
            }
        }
        //requiresPermission("packy.send.others")
        //executes(ArgumentTypes.players().resolve()) { players ->
        //    packy.launch {
        //        players.forEach {
        //            PackyServer.sendPack(it)
        //        }
        //        sender.success("Sent pack to ${players.take(6).joinToString(",") { it.name }}...")
        //    }
        //}
    }
    "bypass" {
        executes.asPlayer {
            player.packyData.bypassForced = !player.packyData.bypassForced
            when (player.packyData.bypassForced) {
                true -> sender.success("Bypassing forced pack")
                else -> sender.error("No longer bypassing forced pack")
            }
        }
    }
    "debug" {
        executes.asPlayer {
            player.packyData.templates.mapNotNull { (packy.templates[it.key] ?: return@mapNotNull null) to it.value }
                .map {
                    Component.textOfChildren(
                        Component.text(
                            it.first.id, when {
                                it.first.default && it.first.required -> NamedTextColor.GOLD
                                it.first.default -> NamedTextColor.YELLOW
                                it.first.required -> NamedTextColor.RED
                                else -> NamedTextColor.AQUA
                            }
                        ),
                        Component.text(": "),
                        Component.text(it.second, if (it.second) NamedTextColor.GREEN else NamedTextColor.DARK_RED),
                    )
                }.forEach(sender::sendMessage)
        }
    }
}