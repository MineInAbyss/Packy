package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.messaging.*
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import korlibs.datastructure.ByteArray2
import korlibs.datastructure.CacheMap
import kotlinx.coroutines.delay
import org.bukkit.entity.Player
import team.unnamed.creative.BuiltResourcePack
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.server.handler.ResourcePackRequestHandler
import team.unnamed.creative.server.ResourcePackServer


object PackyServer {
    var packServer: ResourcePackServer? = null
    val cachedPacks: CacheMap<TemplateIds, BuiltResourcePack> = CacheMap(packy.config.cachedPackAmount)
    val cachedPacksByteArray: CacheMap<TemplateIds, ByteArray> = CacheMap(packy.config.cachedPackAmount)

    fun sendPack(player: Player, resourcePack: BuiltResourcePack) =
        player.setResourcePack(packy.config.server.publicUrl(resourcePack.hash()), resourcePack.hash(), packy.config.force && !player.packyData.bypassForced, packy.config.prompt.miniMsg())

    fun startServer() {
        logSuccess("Started Packy-Server...")
        val (ip, port) = packy.config.server.let { it.ip to it.port }
        packServer = ResourcePackServer.server().address(ip, port).handler(handler).build()
        packServer?.start()
    }

    fun stopServer() {
        logError("Stopping Packy-Server...")
        packServer?.stop(0)
    }

    private var handler = ResourcePackRequestHandler { request, exchange ->
        val data = request?.uuid()?.toPlayer()?.packyData?.enabledPackIds?.let { cachedPacksByteArray[it] } ?: MinecraftResourcePackWriter.minecraft().build(packy.defaultPack).data().toByteArray()
        exchange.responseHeaders["Content-Type"] = "application/zip"
        exchange.sendResponseHeaders(200, data.size.toLong())
        exchange.responseBody.use { responseStream -> responseStream.write(data) }
    }
}
