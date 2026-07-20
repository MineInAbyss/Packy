package com.mineinabyss.packy.listener

import com.mineinabyss.idofront.plugin.Plugins
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.idofront.plugin.unregisterListeners
import com.mineinabyss.packy.PackyGenerator
import com.mineinabyss.packy.PackySquash
import com.mineinabyss.packy.config.PackyContext
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.listener.TemplateLoadTriggers.unregisterTemplateHandlers
import com.ticxo.modelengine.api.events.ModelRegistrationEvent
import com.ticxo.modelengine.api.generator.ModelGenerator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

object TemplateLoadTriggers {

    fun registerTemplateHandlers() {
        unregisterTemplateHandlers()
        packy.templates.forEach { it.loadTrigger.registerLoadHandler(it) }
    }

    fun unregisterTemplateHandlers() {
        runCatching {
            packy.templates.forEach { t -> t.triggerListener?.let { packy.unregisterListeners(it) } }
        }
    }
}

@Serializable
sealed interface LoadTrigger {
    fun registerLoadHandler(template: PackyTemplate)

    @Serializable
    @SerialName("None")
    data object NoTrigger : LoadTrigger {
        override fun registerLoadHandler(template: PackyTemplate) {
        }
    }

    @Serializable
    @SerialName("ModelEngine")
    data object ModelEngineTrigger : LoadTrigger {
        override fun registerLoadHandler(template: PackyTemplate) {
            if (!Plugins.isEnabled("ModelEngine")) return

            val id = template.id
            unregisterTemplateHandlers()
            val listener = object : Listener {
                @EventHandler
                fun ModelRegistrationEvent.onMegPackZipped() {

                    if (phase != ModelGenerator.Phase.FINISHED) return
                    packy.logger.w("ModelEngine loadTrigger detected...")
                    val megPack = packy.server.pluginsFolder.resolve("ModelEngine/resource pack.zip").takeIf { it.exists() }
                    packy.server.pluginsFolder.resolve("ModelEngine/resource pack").takeIf { it.exists() }
                    ?: return packy.logger.e("ModelEngine pack is missing, skipping loadTrigger for $id-template")
                    megPack?.copyTo(template.path.toFile(), overwrite = true)

                    template.clearFromCache()
                    packy.logger.s("Copying ModelEngine-pack for $id-template")

                    if (packy.config.packSquash.enabled) {
                        packy.logger.i("Starting PackSquash process for $id-template...")
                        PackySquash.squashPackyTemplate(template)
                        packy.logger.s("Finished PackSquash process for $id-template")
                    }
                }
            }
            template.triggerListener = listener
            packy.listeners(listener)
        }
    }

    fun PackyTemplate.clearFromCache() {
        PackyGenerator.cachedPacks.keys.removeIf { id in it }
        PackyGenerator.cachedPacksByteArray.keys.removeIf { id in it }
    }
}
