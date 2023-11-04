package com.mineinabyss.packy

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.guiy.inventory.guiy
import com.mineinabyss.idofront.commands.arguments.optionArg
import com.mineinabyss.idofront.commands.arguments.playerArg
import com.mineinabyss.idofront.commands.arguments.stringArg
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.messaging.*
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyDownloader
import com.mineinabyss.packy.helpers.PackyServer
import com.mineinabyss.packy.menus.picker.PackPicker
import com.mineinabyss.packy.menus.picker.PackyMainMenu
import com.sun.jna.platform.unix.solaris.LibKstat.KstatNamed.UNION.STR
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class PackyCommands : IdofrontCommandExecutor(), TabCompleter {
    override val commands = commands(packy.plugin) {
        "packy" {
            "github" {
                "fetch" {
                    val id: String by optionArg(packy.templates.entries.filter { it.value.githubUrl != null }.map { it.key }.toMutableList().apply { add("ALL") }) { default = "ALL" }
                    action {
                        packy.plugin.launch(packy.plugin.asyncDispatcher) {
                            when (id) {
                                "ALL" -> {
                                    sender.warn("Downloading all templates...")
                                    PackyDownloader.downloadTemplates()
                                    sender.success("Downloaded all templates!")
                                }
                                else -> {
                                    val template = packy.templates.entries.find { it.key == id }?.value ?: return@launch sender.error("No template with given ID")
                                    sender.warn("Downloading template $id...")
                                    PackyDownloader.downloadAndExtractTemplate(template)
                                    sender.success("Downloaded template $id!")
                                }
                            }

                        }
                    }
                }
            }
            "reload" {
                action {
                    packy.plugin.createPackyContext()
                    sender.success("Packy has been reloaded!")
                }
            }
            "send" {
                val player: Player by playerArg { default = sender as? Player }
                action {
                    PackyServer.sendPack(player)
                    sender.success("Sent pack to ${player.name}")
                }
            }
            "gui" {
                val player: Player by playerArg { default = sender as? Player }
                action {
                    guiy { PackyMainMenu(player) }
                }
            }
            "server" {
                val state: String by optionArg(listOf("start", "stop"))
                action {
                    when {
                        state == "start" && !PackyServer.serverStarted -> {
                            PackyServer.startServer()
                            sender.success("PackyServer started on ${packy.config.server.ip}:${packy.config.server.port}...")
                        }
                        else -> {
                            PackyServer.stopServer()
                            sender.error("PackyServer stopped...")
                        }
                    }
                }
            }
            "bypass" {
                val player: Player by playerArg { default = sender as? Player }
                action {
                    player.packyData.bypassForced = !player.packyData.bypassForced
                    when (player.packyData.bypassForced) {
                        true -> sender.success("Bypassing forced pack")
                        else -> sender.error("No longer bypassing forced pack")
                    }
                }
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return if (command.name == "packy") {
            when (args.size) {
                1 -> listOf("reload", "gui", "server", "send", "github").filter { it.startsWith(args[0]) }
                2 -> when(args[0]) {
                    "server" -> listOf("start", "stop")
                    "send", "gui", "bypass" -> packy.plugin.server.onlinePlayers.map { it.name }
                    "github" -> listOf("fetch")
                    else -> listOf()
                }.filter { it.startsWith(args[1]) }
                3 -> when(args[0]) {
                    "github" -> packy.templates.entries.filter { it.value.githubUrl != null }.map { it.key }.toMutableList().apply { add("ALL") }
                    else -> listOf()
                }.filter { it.startsWith(args[2]) }
                else -> listOf()
            }
        } else listOf()
    }
}
