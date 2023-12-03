package com.mineinabyss.packy.helpers

import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import org.bukkit.entity.Player
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.server.ResourcePackServer
import team.unnamed.creative.server.handler.ResourcePackRequestHandler
import java.net.URI
import java.util.concurrent.Executors


object PackyServer {
    var packServer: ResourcePackServer? = null

    suspend fun sendPack(player: Player) {
        val templateIds = player.packyData.enabledPackIds
        val resourcePack = PackyGenerator.getOrCreateCachedPack(templateIds).await()
        player.setResourcePack(
            packy.config.server.publicUrl(resourcePack.hash(), templateIds),
            resourcePack.hash(),
            packy.config.force && !player.packyData.bypassForced,
            packy.config.prompt.miniMsg()
        )
    }

    fun startServer() {
        logSuccess("Started Packy-Server...")
        val (ip, port) = packy.config.server.let { it.ip to it.port }
        packServer = ResourcePackServer.server().address(ip, port).handler(handler).build()
        packServer?.httpServer()?.executor = Executors.newFixedThreadPool(20)
        packServer?.start()
    }

    fun stopServer() {
        logError("Stopping Packy-Server...")
        packServer?.stop(0)
    }

    fun URI.parseTemplateIds(): TemplateIds? {
        // split query string into map
        val queryMap = query.split('&').mapNotNull {
            it.split('=', limit = 2).takeIf { it.size == 2 }?.let { it.first() to it.last() }
        }.toMap()
        return queryMap["packs"]?.split(",")?.toSortedSet()
    }

    private val handler = ResourcePackRequestHandler { request, exchange ->
        val data = exchange.requestURI.parseTemplateIds()
            ?.let { templateIds -> PackyGenerator.cachedPacksByteArray[templateIds] }
            ?: MinecraftResourcePackWriter.minecraft().build(packy.defaultPack).data().toByteArray()
        exchange.responseHeaders["Content-Type"] = "application/zip"
        exchange.sendResponseHeaders(200, data.size.toLong())
        exchange.responseBody.use { responseStream -> responseStream.write(data) }
    }
}
