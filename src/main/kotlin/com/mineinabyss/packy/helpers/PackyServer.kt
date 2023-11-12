package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.idofront.messaging.broadcastVal
import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import korlibs.datastructure.CacheMap
import kotlinx.coroutines.delay
import org.bukkit.entity.Player
import team.unnamed.creative.BuiltResourcePack
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.server.handler.ResourcePackRequestHandler
import team.unnamed.creative.server.ResourcePackServer


object PackyServer {
    var packUploaded: Boolean = false
    var serverStarted: Boolean = false
    lateinit var packServer: ResourcePackServer
    val cachedPacks: CacheMap<TemplateIds, BuiltResourcePack> = CacheMap(packy.config.cachedPackAmount)

    fun sendPack(player: Player, resourcePack: BuiltResourcePack) =
        player.setResourcePack(packy.config.server.publicAddress, resourcePack.hash(), packy.config.force && !player.packyData.bypassForced, packy.config.prompt.miniMsg())

    fun startServer() {
        logSuccess("Started Packy-Server...")
        val (ip, port) = packy.config.server.let { it.ip to it.port }
        packServer = ResourcePackServer.server().address(ip, port).handler(handler).build()
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

    private var handler = ResourcePackRequestHandler { request, exchange ->
        broadcast(1)
        val data = request?.uuid()?.toPlayer()?.packyData?.enabledPackIds?.let { cachedPacks[it] }?.data()?.toByteArray() ?: return@ResourcePackRequestHandler
        broadcast(2)
        exchange.responseHeaders["Content-Type"] = "application/zip"
        exchange.sendResponseHeaders(200, data.size.toLong())
        exchange.responseBody.use { responseStream -> responseStream.write(data) }
    }
}
