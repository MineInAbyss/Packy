package com.mineinabyss.packy.listener

import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.logInfo
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.plugin.Plugins
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyGenerator
import com.mineinabyss.packy.helpers.PackySquash
import com.ticxo.modelengine.api.events.ModelRegistrationEvent
import com.ticxo.modelengine.api.generator.ModelGenerator
import io.th0rgal.oraxen.api.events.OraxenPackPreUploadEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

object TemplateLoadTriggers {

    fun registerTemplateHandlers() {
        packy.templates.values.forEach { it.loadTrigger?.registerLoadHandler(it) }
    }

    enum class LoadTrigger {
        MODELENGINE, HAPPYHUD, ORAXEN;

        fun registerLoadHandler(template: PackyTemplate) {
            packy.plugin.listeners(when {
                this == MODELENGINE && Plugins.isEnabled("ModelEngine") -> object : Listener {
                    @EventHandler
                    fun ModelRegistrationEvent.onMegPackZipped() {
                        val id = template.id
                        if (phase != ModelGenerator.Phase.POST_ZIPPING) return
                        logSuccess("ModelEngien loadTrigger detected...")
                        val megPack = packy.plugin.server.pluginsFolder.resolve("ModelEngine/resource pack.zip").takeIf { it.exists() }
                            ?: return logError("ModelEngine pack is missing. Skipping loadTrigger for $id-template")
                        megPack.copyTo(template.path.toFile())

                        PackyGenerator.cachedPacks.keys.removeIf { id in it }
                        PackyGenerator.cachedPacksByteArray.keys.removeIf { id in it }
                        logSuccess("Copying ModelEngine pack for $id")

                        if (packy.config.packSquash.enabled) {
                            logInfo("Starting PackSquash process for $id-template...")
                            PackySquash.squashPackyTemplate(template)
                            logSuccess("Finished PackSquash process for $id-template")
                        }
                    }
                }
                this == HAPPYHUD && Plugins.isEnabled("HappyHUD") -> {
                    return logError("HappyHUD load trigger not yet implemented")
                }
                this == ORAXEN && Plugins.isEnabled("Oraxen") -> object : Listener {
                    @EventHandler
                    fun OraxenPackPreUploadEvent.onOraxenPackPreUpload() {
                        isCancelled = true
                        logError("Oraxen load trigger not yet implemented")
                    }
                }

                else -> return
            })
        }
    }
}
