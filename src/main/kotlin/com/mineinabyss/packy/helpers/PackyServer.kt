package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.delay
import org.bukkit.entity.Player
import team.unnamed.creative.BuiltResourcePack
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.server.ResourcePackRequest
import team.unnamed.creative.server.ResourcePackRequestHandler
import team.unnamed.creative.server.ResourcePackServer
import java.util.*


object PackyServer {
    var packUploaded: Boolean = false
    var serverStarted: Boolean = false
    lateinit var packServer: ResourcePackServer
    val playerPacks: MutableMap<UUID, ResourcePack> = mutableMapOf()
    var Player.playerPack
        get(): ResourcePack? = playerPacks[uniqueId] ?: run { PackyGenerator.createPlayerPack(this); null }
        set(value) {
            value?.let {
                playerPacks[uniqueId] = value
            } ?: playerPacks.remove(uniqueId)
        }
    val Player.builtPlayerPack: BuiltResourcePack? get() = this.playerPack.let { MinecraftResourcePackWriter.minecraft().build(it) }

    fun sendPack(player: Player) {
        packy.plugin.launch {
            if (player.uniqueId in PackyGenerator.activeGeneratorJob)
                logError("Pack is still being generated, please wait...")
            while (player.playerPack == null) delay(10.ticks)

            val hash = player.builtPlayerPack!!.hash()
            player.setResourcePack(packy.config.server.url(hash), hash, packy.config.force && !player.packyData.bypassForced, packy.config.prompt.miniMsg())
        }
    }

    fun startServer() {
        logSuccess("Started Packy-Server...")
        val (ip, port) = packy.config.server.let { it.ip to it.port }
        packServer = ResourcePackServer.builder().address(ip, port).handler(handler).build()
        packServer.start()
        serverStarted = true
        packUploaded = true
    }

    fun stopServer() {
        if (!serverStarted) return
        logError("Stopping Packy-Server...")
        packServer.stop(0)
        serverStarted = false
        packUploaded = false
    }

    private var handler = ResourcePackRequestHandler { request: ResourcePackRequest, exchange: HttpExchange ->
        val data = request.uuid().toPlayer()?.builtPlayerPack?.data()?.toByteArray() ?: return@ResourcePackRequestHandler
        exchange.responseHeaders["Content-Type"] = "application/zip"
        exchange.sendResponseHeaders(200, data.size.toLong())
        exchange.responseBody.use { responseStream -> responseStream.write(data) }
    }
}
