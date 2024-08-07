package com.mineinabyss.packy

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.guiy.inventory.guiy
import com.mineinabyss.idofront.commands.brigadier.commands
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.menus.picker.PackyMainMenu
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object PackyCommands {
    fun registerCommands() {
        packy.plugin.commands {
            "packy" {
                "reload" {
                    executes {
                        packy.plugin.createPackyContext()
                        sender.success("Packy has been reloaded!")
                        if (packy.config.sendOnReload) packy.plugin.server.onlinePlayers.forEach {
                            packy.plugin.launch {
                                PackyServer.sendPack(it)
                            }
                        }
                    }
                }
                "menu" {
                    playerExecutes {
                        guiy { PackyMainMenu(player) }
                    }
                }
                "send" {
                    playerExecutes {
                        packy.plugin.launch {
                            PackyServer.sendPack(player)
                            sender.success("Sent pack to ${player.name}")
                        }
                    }
                    val players by ArgumentTypes.players()
                    executes {
                        packy.plugin.launch {
                            players().forEach {
                                PackyServer.sendPack(it)
                            }
                            sender.success("Sent pack to ${players().take(6).joinToString(",") { it.name }}...")
                        }
                    }
                }
                "bypass" {
                    playerExecutes {
                        player.packyData.bypassForced = !player.packyData.bypassForced
                        when (player.packyData.bypassForced) {
                            true -> sender.success("Bypassing forced pack")
                            else -> sender.error("No longer bypassing forced pack")
                        }
                    }
                }
            }
        }
    }
}
