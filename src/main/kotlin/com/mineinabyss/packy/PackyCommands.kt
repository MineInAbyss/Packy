package com.mineinabyss.packy

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.guiy.inventory.guiy
import com.mineinabyss.idofront.commands.arguments.genericArg
import com.mineinabyss.idofront.commands.arguments.optionArg
import com.mineinabyss.idofront.commands.arguments.playerArg
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.extensions.actions.ensureSenderIsPlayer
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.messaging.*
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyDownloader
import com.mineinabyss.packy.helpers.PackyGenerator
import com.mineinabyss.packy.helpers.PackyServer
import com.mineinabyss.packy.menus.picker.PackyMainMenu
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
                    val template: PackyTemplate by genericArg(parseFunction = { passed ->
                        packy.templates.filter { it.value.githubDownload != null }[passed]!!
                    })
                    action {
                        packy.plugin.launch(packy.plugin.asyncDispatcher) {
                            sender.warn("Downloading template ${template.id}...")
                            PackyDownloader.updateGithubTemplate(template)
                            sender.success("Downloaded template ${template.id}!")
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
                playerAction {
                    packy.plugin.launch {
                        PackyServer.sendPack(player, PackyGenerator.getOrCreateCachedPack(player).await())
                        sender.success("Sent pack to ${player.name}")
                    }
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
                        state == "start" && PackyServer.packServer != null -> {
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
                ensureSenderIsPlayer()
                action {
                    val player = sender as Player
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
                    "github" -> packy.templates.entries.filter { it.value.githubDownload != null }.map { it.key }
                    else -> listOf()
                }.filter { it.startsWith(args[2]) }
                else -> listOf()
            }
        } else listOf()
    }
}
