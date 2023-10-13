package com.mineinabyss.packy

import com.mineinabyss.guiy.inventory.guiy
import com.mineinabyss.idofront.commands.entrypoint.CommandDSLEntrypoint
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyServer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class PackyCommands : IdofrontCommandExecutor(), TabCompleter {
    override val commands = commands(packy.plugin) {
        "packy" {
            "reload" {
                action {
                    packy.plugin.createPackyContext()
                    sender.success("Packy has been reloaded!")
                }
            }
            "send" {
                playerAction {
                    PackyServer.sendToPlayer(player)
                }
            }
            "gui" {
                playerAction {
                    guiy {  }
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
                1 -> listOf("reload").filter { it.startsWith(args[0]) }
                else -> listOf()
            }
        } else emptyList()
    }
}
