package com.mineinabyss.packy

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.PackyGenerator.cachedPacksByteArray
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.TemplateIds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import team.unnamed.creative.server.ResourcePackServer
import team.unnamed.creative.server.handler.ResourcePackRequestHandler
import java.net.URI
import java.util.concurrent.Executors


object PackyServer {
    var packServer: ResourcePackServer? = null

    suspend fun sendPack(player: Player) {
        val templateIds = player.packyData.enabledPackIds
        val actionBarJob = packy.plugin.launch { while (isActive) player.sendPackGeneratingActionBar() }
        val resourcePack = PackyGenerator.getOrCreateCachedPack(templateIds).apply { invokeOnCompletion { actionBarJob.cancel() } }.await()

        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
            .packs(resourcePack.resourcePackInfo).replace(true)
            .required(packy.config.force && !player.packyData.bypassForced)
            .prompt(packy.config.prompt?.miniMsg())
        )
    }

    private suspend fun Player.sendPackGeneratingActionBar() {
        sendActionBar(Component.text("Generating ResourcePack.", NamedTextColor.RED))
        delay(10.ticks)
        sendActionBar(Component.text("Generating ResourcePack..", NamedTextColor.RED))
        delay(10.ticks)
        sendActionBar(Component.text("Generating ResourcePack...", NamedTextColor.RED))
        delay(10.ticks)
    }

    fun startServer() {
        packy.logger.s("Starting Packy-Server...")
        val (ip, port) = packy.config.server.let { it.ip to it.port }
        packServer = ResourcePackServer.server().address(ip, port).handler(handler).executor(Executors.newFixedThreadPool(20)).build()
        packServer?.start()
    }

    fun stopServer() {
        packy.logger.i("Stopping Packy-Server...")
        packServer?.stop(0)
    }

    private fun URI.parseTemplateIds(): TemplateIds? {
        // split query string into map
        val queryMap = query.split('&').mapNotNull {
            it.split('=', limit = 2).takeIf { it.size == 2 }?.let { it.first() to it.last() }
        }.toMap()
        return queryMap["packs"]?.split(",")?.toSortedSet()
    }

    private val handler = ResourcePackRequestHandler { _, exchange ->
        val data = exchange.requestURI.parseTemplateIds()?.let(cachedPacksByteArray::get)
            ?: ResourcePacks.resourcePackWriter.build(packy.defaultPack).data().toByteArray()
        exchange.responseHeaders["Content-Type"] = "application/zip"
        exchange.sendResponseHeaders(200, data.size.toLong())
        exchange.responseBody.use { responseStream -> responseStream.write(data) }
    }
}
