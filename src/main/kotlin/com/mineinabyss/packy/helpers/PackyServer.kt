package com.mineinabyss.packy.helpers

import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.logInfo
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.PackyGenerator
import com.mineinabyss.packy.config.packy
import org.bukkit.entity.Player
import team.unnamed.creative.BuiltResourcePack
import team.unnamed.creative.server.ResourcePackServer

object PackyServer {
    val packServer: ResourcePackServer = ResourcePackServer.builder()
        .address(packy.config.server.ip, packy.config.server.port)
        .pack(PackyGenerator.buildPack())
        .build()
    fun startServer() {
        logSuccess("Started Packy-Server...")
        packServer.start()
    }

    fun stopServer() {
        logError("Stopping Packy-Server...")
        packServer.stop(0)
    }

    fun sendToPlayer(player: Player, builtPack: BuiltResourcePack? = null) {
        val hash = (builtPack ?: PackyGenerator.buildPack()).hash()
        val (ip, port) = packServer.httpServer().address.let { it.hostString to it.port }
        player.setResourcePack("http://$ip:$port/${hash}.zip", hash, packy.config.force, packy.config.prompt.miniMsg())
    }
}
